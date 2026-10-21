import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_dio.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/dev_log.dart';
import '../../core/util/json_read.dart';
import '../../core/util/json_shape.dart';
import '../models/account.dart';
import '../models/comment.dart';
import '../models/feed_page.dart';
import '../models/hot_search.dart';
import '../models/notice.dart';
import '../models/paged.dart';
import '../models/status.dart';
import '../models/topic.dart';
import '../models/user_profile.dart';
import 'page_config.dart';

final weiboApiProvider = Provider<WeiboApi>((ref) {
  final dio = ref.watch(weiboDioFactoryProvider)(ref.watch(cookieJarProvider));
  ref.onDispose(dio.close);
  return WeiboApi(
    dio,
    onSessionExpired: () => ref.read(sessionProvider.notifier).expire(),
  );
});

/// A page of search results, with the topic if one was searched for.
typedef SearchResults = ({Paged<Status> statuses, Topic? topic});

/// The pid in picupload's answer [body], or null if the upload failed.
///
/// It answers `{"code":"A00006","data":{"pics":{"pic_1":{"pid":…}}}}`, the
/// newer upload `{"ret":true,"pic":{"pid":…}}`; either may follow a script
/// prelude.
String? parseUploadedPid(String body) {
  final start = body.indexOf('{');
  if (start < 0) return null;
  final Object? json;
  try {
    json = jsonDecode(body.substring(start));
  } on FormatException {
    return null;
  }
  final map = readMap(json);
  if (map == null) return null;
  if (map['ret'] == true) return readString(readMap(map['pic'])?['pid']);
  if (map['code'] != 'A00006') return null;
  final pics = readMap(readMap(map['data'])?['pics']);
  return readString(readMap(pics?['pic_1'])?['pid']);
}

/// Weibo's web endpoints, as used by weibo.com in a browser.
class WeiboApi {
  WeiboApi(this._dio, {this.onSessionExpired});

  static const _web = 'https://weibo.com';

  final Dio _dio;

  /// Called whenever Weibo rejects the session, before the exception is
  /// rethrown to the caller.
  final void Function()? onSessionExpired;

  /// The signed-in account; throws [SessionExpiredException] for visitors.
  ///
  /// weibo.com has no JSON endpoint for this; the web app reads the user from
  /// the `$CONFIG` object embedded in its home page, and so do we.
  Future<Account> fetchCurrentAccount() async {
    final html = await getPage('$_web/');
    final config = extractPageConfig(html);
    if (config == null) {
      devLog('weibo.com page has no \$CONFIG (${html.length} chars)');
      throw const WeiboApiException('无法识别当前登录的账号');
    }
    final account = Account.tryFromPageConfig(config);
    if (account == null) {
      devLog('no user in \$CONFIG: ${describeShape(config, maxDepth: 1)}');
      throw const SessionExpiredException();
    }
    return account;
  }

  /// A page of the "最新微博" timeline of [uid]: the newest page, or the one
  /// after [maxId] (a previous page's `FeedPage.nextCursor`).
  Future<FeedPage> fetchHomeTimeline({
    required String uid,
    String? maxId,
  }) async {
    final json = await getJson(
      '$_web/ajax/feed/friendstimeline',
      query: {
        // The built-in "最新微博" group; see PLAN §8 item 3.
        'list_id': '11000$uid',
        'refresh': 4,
        'count': 20,
        if (maxId == null) 'since_id': 0 else 'max_id': maxId,
      },
    );
    final page = FeedPage.fromJson(json);
    devLog(
      'timeline: ${page.statuses.length} statuses, '
      'more=${page.nextCursor != null}',
    );
    return page;
  }

  /// Status [id] with its full text (timelines truncate long posts), current
  /// counts and freshly signed video URLs.
  Future<Status> fetchStatus(String id) async {
    final json = await getJson(
      '$_web/ajax/statuses/show',
      // With this flag `text` holds the whole post as HTML; the longtext
      // endpoint only has it as plain text (PLAN §8).
      query: {'id': id, 'isGetLongText': 'true'},
    );
    final status = Status.tryFromJson(json);
    if (status == null) throw const WeiboApiException('微博不存在或已被删除');
    return status;
  }

  /// A page of the comments on status [statusId] by [authorId], continuing
  /// after [maxId] (a previous page's `Paged.nextCursor`).
  Future<Paged<Comment>> fetchComments({
    required String statusId,
    required String authorId,
    required CommentOrder order,
    String? maxId,
  }) async {
    final json = await getJson(
      '$_web/ajax/statuses/buildComments',
      query: {
        'is_reload': 1,
        'id': statusId,
        'is_show_bulletin': 2,
        'is_mix': 0,
        'count': 20,
        'uid': authorId,
        'fetch_level': 0,
        'flow': order == CommentOrder.hot ? 0 : 1,
        'locale': 'zh-CN',
        'max_id': ?maxId,
      },
    );
    return _commentPage(json);
  }

  /// A page of the replies in the thread of comment [commentId], on a status
  /// by [authorId].
  Future<Paged<Comment>> fetchReplies({
    required String commentId,
    required String authorId,
    String? maxId,
  }) async {
    final json = await getJson(
      '$_web/ajax/statuses/buildComments',
      query: {
        'is_reload': 1,
        'id': commentId,
        'is_show_bulletin': 2,
        'is_mix': 1,
        'fetch_level': 1,
        'max_id': maxId ?? 0,
        'count': 20,
        'uid': authorId,
        'locale': 'zh-CN',
      },
    );
    return _commentPage(json);
  }

  // Comment lists continue from `max_id`, which is 0 on the last page.
  static Paged<Comment> _commentPage(Map<String, Object?> json) {
    final comments = [
      for (final c in readMaps(json['data'])) ?Comment.tryFromJson(c),
    ];
    final maxId = readString(json['max_id']);
    devLog('comments: ${comments.length}, more=${maxId != '0'}');
    return Paged(
      comments,
      nextCursor: comments.isEmpty || maxId == null || maxId == '0'
          ? null
          : maxId,
      total: readInt(json['total_number']),
    );
  }

  /// Page [page] (from 1) of the reposts of status [statusId], newest first.
  Future<Paged<Status>> fetchReposts({
    required String statusId,
    int page = 1,
  }) async {
    final json = await getJson(
      '$_web/ajax/statuses/repostTimeline',
      // Weibo sends 20 a page whatever `count` asks for.
      query: {'id': statusId, 'page': page, 'moduleID': 'feed', 'count': 20},
    );
    final reposts = [
      for (final s in readMaps(json['data'])) ?Status.tryFromJson(s),
    ];
    final maxPage = readInt(json['max_page']) ?? page;
    return Paged(
      reposts,
      nextCursor: reposts.isEmpty || page >= maxPage ? null : '${page + 1}',
      total: readInt(json['total_number']),
    );
  }

  /// The profile of the user with [uid], or else named [screenName] (all
  /// that mentions in a status carry).
  Future<UserProfile> fetchProfile({String? uid, String? screenName}) async {
    assert(uid != null || screenName != null);
    final json = await getJson(
      '$_web/ajax/profile/info',
      query: uid != null ? {'uid': uid} : {'screen_name': screenName},
    );
    // An unknown name still gets ok=1, with no `data` but an empty `user`.
    final profile = UserProfile.tryFromJson(json['data']);
    if (profile == null) throw const WeiboApiException('找不到这个用户');
    return profile;
  }

  /// Page [page] (from 1) of the statuses of user [uid], newest first after
  /// any pinned ones.
  Future<Paged<Status>> fetchUserStatuses({
    required String uid,
    int page = 1,
  }) async {
    final json = await getJson(
      '$_web/ajax/statuses/mymblog',
      query: {'uid': uid, 'page': page, 'feature': 0},
    );
    final data = readMap(json['data']);
    final statuses = [
      for (final s in readMaps(data?['list'])) ?Status.tryFromJson(s),
    ];
    // The page number alone pages through; `since_id` is empty on the last
    // page (PLAN §8).
    final last = statuses.isEmpty || data?['since_id'] == '';
    devLog('user statuses p$page: ${statuses.length}, more=${!last}');
    return Paged(
      statuses,
      nextCursor: last ? null : '${page + 1}',
      total: readInt(data?['total']),
    );
  }

  Future<HotSearch> fetchHotSearch() async {
    final json = await getJson('$_web/ajax/side/hotSearch');
    return HotSearch.fromJson(readMap(json['data']) ?? const {});
  }

  /// Page [page] (from 1) of the posts across Weibo that match [query],
  /// newest first. A `#话题#` finds the posts in that topic, and comes with
  /// the topic.
  ///
  /// This is weibo.com's search of the home feed, which sends `atten=1` to
  /// keep to followed users; without it, it searches everyone (PLAN §8).
  Future<SearchResults> searchStatuses(String query, {int page = 1}) async {
    final json = await getJson(
      '$_web/ajax/statuses/search',
      query: {'q': query, 'count': 20, 'page': page},
    );
    final statuses = [
      for (final s in readMaps(json['statuses'])) ?Status.tryFromJson(s),
    ];
    // Results come in as they are posted, so the next page may repeat the
    // end of this one; only an empty page ends them.
    devLog('search p$page: ${statuses.length}');
    return (
      statuses: Paged(
        statuses,
        nextCursor: statuses.isEmpty ? null : '${page + 1}',
        total: readInt(json['total_number']),
      ),
      topic: Topic.tryFromJson(json['topicHeads']),
    );
  }

  /// Which kinds of notifications are unread.
  ///
  /// weibo.com polls these every 30 s, by JSONP and with its own `source`;
  /// we ask only when the user opens the app (PLAN §2), and plain JSON comes
  /// back without a callback. A failure here never ends the session: this
  /// host isn't the API the session is checked against.
  Future<UnreadNotices> fetchUnreadNotices() async {
    final Response<Object?> response;
    try {
      response = await _dio.get<Object?>(
        'https://rm.api.weibo.com/2/remind/push_count.json',
        queryParameters: {
          'trim_null': 1,
          'with_dm_group': 1,
          'with_reminding': 1,
          'with_settings': 1,
          // weibo.com leaves likes out; we want them.
          'exclude_attitude': 0,
          'with_chat_group_notice': 1,
          'source': 339644097,
          'with_chat_group': 1,
          'with_dm_unread': 1,
        },
      );
    } on DioException catch (e) {
      throw mapDioException(e);
    }
    final data = response.data;
    _logResponse(response, data is Map ? 'map' : '${data.runtimeType}');
    if (response.statusCode != 200 || data is! Map<String, Object?>) {
      throw WeiboApiException(
        '未读消息获取失败（${response.statusCode}）',
        statusCode: response.statusCode,
      );
    }
    return UnreadNotices.fromJson(data);
  }

  /// A page of the comments on the user's posts and replies to them, or
  /// after [maxId] (a previous page's `Paged.nextCursor`).
  Future<Paged<CommentNotice>> fetchCommentNotices({String? maxId}) =>
      _noticePage('message/cmt', 'comments', CommentNotice.tryFromJson, maxId);

  /// A page of the comments that mention the user.
  Future<Paged<CommentNotice>> fetchMentionComments({String? maxId}) =>
      _noticePage(
        'comments/mentions',
        'comments',
        CommentNotice.tryFromJson,
        maxId,
      );

  /// A page of the posts that mention the user.
  Future<Paged<Status>> fetchMentionStatuses({String? maxId}) =>
      _noticePage('statuses/mentions', 'statuses', Status.tryFromJson, maxId);

  /// A page of the likes of the user's posts and comments.
  Future<Paged<LikeNotice>> fetchLikeNotices({String? maxId}) => _noticePage(
    'message/attitudes',
    'attitudes',
    LikeNotice.tryFromJson,
    maxId,
  );

  // The 消息 lists: items under `data.<key>`, newest first, continuing from
  // `next_cursor`, which is 0 on the last page (PLAN §8). Fetching one marks
  // its kind read on Weibo's side.
  Future<Paged<T>> _noticePage<T>(
    String path,
    String key,
    T? Function(Object? json) parse,
    String? maxId,
  ) async {
    final json = await getJson(
      '$_web/ajax/$path',
      query: {'max_id': maxId ?? 0},
    );
    final data = readMap(json['data']);
    final items = [for (final i in readMaps(data?[key])) ?parse(i)];
    final next = readString(data?['next_cursor']);
    final more = items.isNotEmpty && next != null && next != '0';
    devLog('$path: ${items.length}, more=$more');
    return Paged(
      items,
      nextCursor: more ? next : null,
      total: readInt(data?['total_number']),
    );
  }

  /// Likes status [id], or takes the like back when [liked] is false.
  Future<void> likeStatus(String id, {bool liked = true}) async => _checkLike(
    await postForm('$_web/ajax/statuses/${liked ? 'setLike' : 'cancelLike'}', {
      'id': id,
    }),
    liked: liked,
  );

  /// Likes comment [id], or takes the like back when [liked] is false.
  Future<void> likeComment(String id, {bool liked = true}) async => _checkLike(
    await postForm(
      '$_web/ajax/statuses/${liked ? 'updateLike' : 'destroyLike'}',
      {'object_id': id, 'object_type': 'comment'},
    ),
    liked: liked,
  );

  // Some answers carry a `result`, and say ok even when it is false: taking
  // a like back right after giving it does nothing, for one (PLAN §8).
  static void _checkLike(Map<String, Object?> json, {required bool liked}) {
    if (json['result'] == false) {
      throw WeiboApiException(liked ? '点赞没有成功，请稍后再试' : '取消赞没有成功，请稍后再试');
    }
  }

  /// Comments [text] on status [statusId], or with [replyTo] replies to that
  /// comment (or reply) under it. Returns the new comment as Weibo shows it,
  /// or null when its answer has none.
  Future<Comment?> postComment({
    required String statusId,
    required String text,
    String? replyTo,
  }) async {
    final json = await postForm(
      '$_web/ajax/comments/${replyTo == null ? 'create' : 'reply'}',
      {
        'id': statusId,
        'cid': ?replyTo,
        'comment': text,
        // Don't also repost it or comment on the retweeted post.
        'is_repost': 0,
        'comment_ori': 0,
        'is_comment': 0,
      },
    );
    return Comment.tryFromJson(json['data']);
  }

  /// Deletes comment (or reply) [id], which the user wrote.
  Future<void> deleteComment(String id) =>
      postForm('$_web/ajax/statuses/destroyComment', {'cid': id});

  /// Uploads a picture for a new post and returns its pid, as weibo.com's
  /// composer does: the file's bytes as they are, to picupload.
  Future<String> uploadPicture(List<int> bytes) => _guard(() async {
    final response = await _dio.post<String>(
      'https://picupload.weibo.com/interface/pic_upload.php',
      queryParameters: {'app': 'miniblog', 's': 'json', 'p': 1, 'data': 1},
      // As a stream: a list would go as its JSON text.
      data: Stream.value(bytes),
      options: Options(
        responseType: ResponseType.plain,
        // What the web sends, without a boundary: the body is the file.
        contentType: 'multipart/form-data',
        headers: {Headers.contentLengthHeader: bytes.length},
        extra: const {kPlainCrossOrigin: true},
        sendTimeout: const Duration(minutes: 2),
        receiveTimeout: const Duration(minutes: 2),
      ),
    );
    final status = response.statusCode ?? 0;
    final body = response.data ?? '';
    _logResponse(response, '${body.length} chars');
    if (isLoginHost(response.realUri.host)) {
      throw const SessionExpiredException();
    }
    if (isRateLimitStatus(status)) throw RateLimitedException(status);
    final pid = parseUploadedPid(body);
    if (pid == null) {
      devLog('pic_upload: $status ${describeShape(_tryDecode(body))}');
      throw WeiboApiException('图片上传失败（$status）', statusCode: status);
    }
    return pid;
  });

  /// Posts [text] with the uploaded pictures [pids], for [visibility].
  /// Returns the new status as Weibo shows it, or null when its answer has
  /// none.
  Future<Status?> postStatus({
    required String text,
    List<String> pids = const [],
    StatusVisibility visibility = StatusVisibility.public,
  }) async {
    final json = await postForm('$_web/ajax/statuses/update', {
      'content': text,
      if (pids.isNotEmpty)
        'pic_id': jsonEncode([
          for (final pid in pids) {'type': 'pic', 'pid': pid},
        ]),
      'visible': visibility.code,
    });
    return Status.tryFromJson(json['data']);
  }

  /// Reposts status [id] with [text], for [visibility]; Weibo puts in
  /// 转发微博 when [text] is empty. With [alsoComment] the text also goes as
  /// a comment on it. Returns the new status as Weibo shows it, or null
  /// when its answer has none.
  Future<Status?> repostStatus({
    required String id,
    String text = '',
    bool alsoComment = false,
    StatusVisibility visibility = StatusVisibility.public,
  }) async {
    final json = await postForm('$_web/ajax/statuses/normal_repost', {
      'id': id,
      // weibo.com leaves it out when empty.
      if (text.isNotEmpty) 'comment': text,
      'is_comment': alsoComment ? 1 : 0,
      // Don't also comment on the retweeted post.
      'comment_ori': 0,
      'is_repost': 0,
      'visible': visibility.code,
    });
    // weibo.com reads it from `data.statuses`.
    final data = json['data'];
    return Status.tryFromJson(readMap(data)?['statuses']) ??
        Status.tryFromJson(data);
  }

  /// Deletes status [id], which the user posted.
  Future<void> deleteStatus(String id) =>
      postForm('$_web/ajax/statuses/destroy', {'id': id});

  Future<Map<String, Object?>> getJson(
    String url, {
    Map<String, Object?>? query,
  }) => _guard(() async {
    final response = await _dio.get<Object?>(url, queryParameters: query);
    final data = response.data;
    _logResponse(response, 'ok=${data is Map ? data['ok'] : data.runtimeType}');
    return unwrapResponse(response);
  });

  /// Posts [body] as a form, as weibo.com does for everything that changes
  /// something.
  Future<Map<String, Object?>> postForm(
    String url,
    Map<String, Object?> body,
  ) => _guard(() async {
    final response = await _dio.post<Object?>(
      url,
      data: body,
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
    final data = response.data;
    _logResponse(
      response,
      data is Map
          ? 'ok=${data['ok']} ${describeShape(data, maxDepth: 0)}'
          : '${data.runtimeType}',
    );
    final json = unwrapResponse(response);
    // An answer can say ok and still carry an error.
    if (json['error'] case final String error when error.isNotEmpty) {
      throw WeiboApiException(error, statusCode: response.statusCode);
    }
    return json;
  });

  /// Fetches an HTML page as a signed-in browser would.
  Future<String> getPage(String url) => _guard(() async {
    final response = await _dio.get<String>(
      url,
      options: Options(
        responseType: ResponseType.plain,
        headers: {'Accept': 'text/html,application/xhtml+xml'},
      ),
    );
    final status = response.statusCode ?? 0;
    _logResponse(response, '${response.data?.length ?? 0} chars');
    if (isLoginHost(response.realUri.host)) {
      throw const SessionExpiredException();
    }
    if (isRateLimitStatus(status)) throw RateLimitedException(status);
    if (status != 200) {
      throw WeiboApiException('请求失败（$status）', statusCode: status);
    }
    return response.data ?? '';
  });

  Future<T> _guard<T>(Future<T> Function() request) async {
    try {
      return await request();
    } on DioException catch (e) {
      throw mapDioException(e);
    } on SessionExpiredException {
      onSessionExpired?.call();
      rethrow;
    }
  }

  static Object? _tryDecode(String body) {
    try {
      return jsonDecode(body);
    } on FormatException {
      return body.length;
    }
  }

  static void _logResponse(Response<Object?> response, String detail) {
    final uri = response.realUri;
    devLog(
      '${response.requestOptions.method} ${response.requestOptions.uri.path} '
      '-> ${response.statusCode} '
      '$detail final=${uri.host}${uri.path}',
    );
  }
}

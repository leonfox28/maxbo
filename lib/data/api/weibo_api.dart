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
import '../models/paged.dart';
import '../models/status.dart';
import 'page_config.dart';

final weiboApiProvider = Provider<WeiboApi>((ref) {
  final dio = ref.watch(weiboDioFactoryProvider)(ref.watch(cookieJarProvider));
  ref.onDispose(dio.close);
  return WeiboApi(
    dio,
    onSessionExpired: () => ref.read(sessionProvider.notifier).expire(),
  );
});

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

  Future<Map<String, Object?>> getJson(
    String url, {
    Map<String, Object?>? query,
  }) => _guard(() async {
    final response = await _dio.get<Object?>(url, queryParameters: query);
    final data = response.data;
    _logResponse(response, 'ok=${data is Map ? data['ok'] : data.runtimeType}');
    return unwrapResponse(response);
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

  static void _logResponse(Response<Object?> response, String detail) {
    final uri = response.realUri;
    devLog(
      'GET ${response.requestOptions.uri.path} -> ${response.statusCode} '
      '$detail final=${uri.host}${uri.path}',
    );
  }
}

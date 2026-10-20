import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/cookie_jar.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_dio.dart';
import 'package:maxbo/core/net/weibo_exception.dart';
import 'package:maxbo/data/api/weibo_api.dart';
import 'package:maxbo/data/models/comment.dart';
import 'package:maxbo/data/models/notice.dart';
import 'package:maxbo/data/models/status.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;
  late ResponseBody Function(RequestOptions) respond;

  setUp(() => adapter = FakeAdapter((o) => respond(o)));

  Map<String, String> query() => adapter.requests.last.uri.queryParameters;

  group('fetchStatus', () {
    test('asks for the full text of long posts', () async {
      respond = (_) =>
          jsonBody({'ok': 1, ...statusJson(id: 42, text: '完整的长文')});

      final status = await fakeApi(adapter).fetchStatus('42');

      expect(status.text, '完整的长文');
      expect(adapter.requests.single.uri.path, '/ajax/statuses/show');
      expect(query(), {'id': '42', 'isGetLongText': 'true'});
    });

    test('reports a response without a status', () {
      respond = (_) => jsonBody({'ok': 1});

      expect(
        fakeApi(adapter).fetchStatus('42'),
        throwsA(isA<WeiboApiException>()),
      );
    });
  });

  group('fetchComments', () {
    test('sends the order and author, and continues from max_id', () async {
      respond = (_) => jsonBody(
        commentsPageJson([commentJson(id: 1)], maxId: 777, total: 99),
      );
      final api = fakeApi(adapter);

      final first = await api.fetchComments(
        statusId: '42',
        authorId: '1001',
        order: CommentOrder.hot,
      );
      expect(adapter.requests.single.uri.path, '/ajax/statuses/buildComments');
      expect(query()['id'], '42');
      expect(query()['uid'], '1001');
      expect(query()['flow'], '0');
      expect(query()['fetch_level'], '0');
      expect(query().containsKey('max_id'), isFalse);
      expect(first.items.single.id, '1');
      expect(first.nextCursor, '777');
      expect(first.total, 99);

      respond = (_) => jsonBody(commentsPageJson([commentJson(id: 2)]));
      final last = await api.fetchComments(
        statusId: '42',
        authorId: '1001',
        order: CommentOrder.time,
        maxId: first.nextCursor,
      );
      expect(query()['flow'], '1');
      expect(query()['max_id'], '777');
      expect(last.nextCursor, isNull);
    });

    test('an empty page ends the list whatever max_id says', () async {
      respond = (_) => jsonBody(commentsPageJson([], maxId: 5));

      final page = await fakeApi(adapter).fetchComments(
        statusId: '42',
        authorId: '1001',
        order: CommentOrder.hot,
      );
      expect(page.nextCursor, isNull);
    });
  });

  test('fetchReplies lists the thread of a comment', () async {
    respond = (_) => jsonBody(commentsPageJson([commentJson(id: 3)]));

    final page = await fakeApi(adapter)
        .fetchReplies(commentId: '600', authorId: '1001');

    expect(query()['id'], '600');
    expect(query()['fetch_level'], '1');
    expect(query()['max_id'], '0');
    expect(page.items.single.id, '3');
    expect(page.nextCursor, isNull);
  });

  test('fetchReposts pages until max_page', () async {
    respond = (_) =>
        jsonBody(repostsPageJson([statusJson(id: 8)], maxPage: 2, total: 30));
    final api = fakeApi(adapter);

    final first = await api.fetchReposts(statusId: '42');
    expect(adapter.requests.single.uri.path, '/ajax/statuses/repostTimeline');
    expect(query()['id'], '42');
    expect(query()['page'], '1');
    expect(first.items.single.id, '8');
    expect(first.nextCursor, '2');
    expect(first.total, 30);

    final second = await api.fetchReposts(statusId: '42', page: 2);
    expect(query()['page'], '2');
    expect(second.nextCursor, isNull);
  });

  group('fetchProfile', () {
    test('looks a user up by uid', () async {
      respond = (_) => jsonBody({
        'ok': 1,
        'data': profileDataJson(user: userJson(id: 1001)),
      });

      final profile = await fakeApi(adapter).fetchProfile(uid: '1001');

      expect(adapter.requests.single.uri.path, '/ajax/profile/info');
      expect(query(), {'uid': '1001'});
      expect(profile.user.id, '1001');
    });

    test('or by name, for mentions', () async {
      respond = (_) => jsonBody({
        'ok': 1,
        'data': profileDataJson(user: userJson(id: 1001, name: '示例用户')),
      });

      final profile = await fakeApi(adapter).fetchProfile(screenName: '示例用户');

      expect(query(), {'screen_name': '示例用户'});
      expect(profile.user.id, '1001');
    });

    test('reports an unknown name, which Weibo answers with ok=1', () {
      respond = (_) => jsonBody({
        'ok': 1,
        'user': <String, Object?>{},
        'message': '用户不存在',
        'abnormal': false,
      });

      expect(
        fakeApi(adapter).fetchProfile(screenName: '没有这个人'),
        throwsA(
          isA<WeiboApiException>().having(
            (e) => e.message,
            'message',
            '找不到这个用户',
          ),
        ),
      );
    });
  });

  group('fetchUserStatuses', () {
    test('pages by number until since_id runs out', () async {
      respond = (_) => jsonBody(
        userStatusesJson([
          statusJson(id: 1, extra: {'isTop': 1}),
          statusJson(id: 2),
        ], total: 30),
      );
      final api = fakeApi(adapter);

      final first = await api.fetchUserStatuses(uid: '1001');
      expect(adapter.requests.single.uri.path, '/ajax/statuses/mymblog');
      expect(query(), {'uid': '1001', 'page': '1', 'feature': '0'});
      expect(first.items.map((s) => s.id), ['1', '2']);
      expect(first.items.first.pinned, isTrue);
      expect(first.nextCursor, '2');
      expect(first.total, 30);

      respond = (_) =>
          jsonBody(userStatusesJson([statusJson(id: 3)], sinceId: ''));
      final last = await api.fetchUserStatuses(uid: '1001', page: 2);
      expect(query()['page'], '2');
      expect(last.items.single.id, '3');
      expect(last.nextCursor, isNull);
    });

    test('an empty page ends the list', () async {
      respond = (_) => jsonBody(userStatusesJson([]));

      final page = await fakeApi(adapter).fetchUserStatuses(uid: '1001');
      expect(page.nextCursor, isNull);
    });
  });

  group('searchStatuses', () {
    test('searches everyone, a page by number at a time', () async {
      respond = (_) => jsonBody(
        searchJson([statusJson(id: 1), statusJson(id: 2)], total: 735),
      );
      final api = fakeApi(adapter);

      final first = await api.searchStatuses('某个词');
      expect(adapter.requests.single.uri.path, '/ajax/statuses/search');
      // No atten=1: that keeps to followed users.
      expect(query(), {'q': '某个词', 'count': '20', 'page': '1'});
      expect(first.statuses.items.map((s) => s.id), ['1', '2']);
      expect(first.statuses.nextCursor, '2');
      expect(first.statuses.total, 735);
      expect(first.topic, isNull);

      await api.searchStatuses('某个词', page: 2);
      expect(query()['page'], '2');
    });

    test('a topic comes with its lead and counts', () async {
      respond = (_) => jsonBody(
        searchJson([
          statusJson(id: 1),
        ], topicHeads: topicHeadsJson(name: '某个话题', summary: '话题导语')),
      );

      final results = await fakeApi(adapter).searchStatuses('#某个话题#');
      expect(query()['q'], '#某个话题#');
      expect(results.topic?.name, '某个话题');
      expect(results.topic?.summary, '话题导语');
    });

    test('only an empty page ends the results', () async {
      respond = (_) => jsonBody(searchJson([], total: 735));

      final page = await fakeApi(adapter).searchStatuses('某个词', page: 37);
      expect(page.statuses.nextCursor, isNull);
    });
  });

  test('fetchHotSearch reads the list', () async {
    respond = (_) =>
        jsonBody(hotSearchJson(realtime: [hotItemJson('第一条', realpos: 1)]));

    final hot = await fakeApi(adapter).fetchHotSearch();

    expect(adapter.requests.single.uri.path, '/ajax/side/hotSearch');
    expect(hot.items.single.word, '第一条');
  });

  group('fetchUnreadNotices', () {
    test('reads which kinds have counts, zero ones left out', () async {
      respond = (_) =>
          jsonBody({'cmt': 2, 'mention_cmt': 0, 'attitude': 1, 'messages': 3});

      final unread = await fakeApi(adapter).fetchUnreadNotices();

      final request = adapter.requests.single;
      expect(request.uri.host, 'rm.api.weibo.com');
      expect(request.uri.path, '/2/remind/push_count.json');
      expect(query()['exclude_attitude'], '0');
      expect(query().containsKey('callback'), isFalse);
      // The session's cookies go along, as they do from weibo.com.
      expect(request.headers['Cookie'], contains('SUB=x'));
      expect(unread.kinds, {NoticeKind.comments, NoticeKind.likes});
    });

    test('a refusal is an error, but never ends the session', () async {
      respond = (_) =>
          jsonBody({'error': 'auth faild!', 'error_code': 21301}, status: 403);
      var expired = false;
      final api = WeiboApi(
        createWeiboDio(
          jar: WeiboCookieJar({
            'weibo.com': {'SUB': 'x'},
          }),
          throttle: RequestThrottle(minInterval: Duration.zero),
        )..httpClientAdapter = adapter,
        onSessionExpired: () => expired = true,
      );

      await expectLater(
        api.fetchUnreadNotices(),
        throwsA(isA<WeiboApiException>()),
      );
      expect(expired, isFalse);
    });
  });

  group('notice lists', () {
    test('page from next_cursor until it is 0', () async {
      respond = (_) => jsonBody(
        noticePageJson('comments', [commentNoticeJson(id: 9)], nextCursor: 8),
      );
      final api = fakeApi(adapter);

      final first = await api.fetchCommentNotices();
      expect(adapter.requests.last.uri.path, '/ajax/message/cmt');
      expect(query(), {'max_id': '0'});
      expect(first.items.single.id, '9');
      expect(first.items.single.status?.text, '被评论的微博');
      expect(first.nextCursor, '8');

      respond = (_) =>
          jsonBody(noticePageJson('comments', [commentNoticeJson(id: 7)]));
      final last = await api.fetchCommentNotices(maxId: '8');
      expect(query(), {'max_id': '8'});
      expect(last.nextCursor, isNull);
    });

    test('an empty page ends the list whatever next_cursor says', () async {
      respond = (_) => jsonBody(noticePageJson('statuses', [], nextCursor: 8));

      final page = await fakeApi(adapter).fetchMentionStatuses();

      expect(adapter.requests.single.uri.path, '/ajax/statuses/mentions');
      expect(page.nextCursor, isNull);
    });

    test('mentioning comments and likes come from their own lists', () async {
      respond = (o) => jsonBody(
        o.uri.path.endsWith('attitudes')
            ? noticePageJson('attitudes', [likeNoticeJson(id: 5)])
            : noticePageJson('comments', [commentNoticeJson(id: 4)]),
      );
      final api = fakeApi(adapter);

      final mentions = await api.fetchMentionComments();
      expect(adapter.requests.last.uri.path, '/ajax/comments/mentions');
      expect(mentions.items.single.id, '4');

      final likes = await api.fetchLikeNotices();
      expect(adapter.requests.last.uri.path, '/ajax/message/attitudes');
      expect(likes.items.single.id, '5');
    });
  });

  group('writes', () {
    Map<String, String> form() => Uri.splitQueryString(adapter.bodies.last);

    test('post forms with the XSRF token, as weibo.com does', () async {
      respond = (_) => jsonBody({'ok': 1, 'attitude': 'heart'});
      final api = WeiboApi(
        createWeiboDio(
          jar: WeiboCookieJar({
            'weibo.com': {'SUB': 'x', 'XSRF-TOKEN': 'tok'},
          }),
          throttle: RequestThrottle(minInterval: Duration.zero),
        )..httpClientAdapter = adapter,
      );

      await api.likeStatus('42');

      final request = adapter.requests.single;
      expect(request.method, 'POST');
      expect(request.uri.path, '/ajax/statuses/setLike');
      expect(request.contentType, Headers.formUrlEncodedContentType);
      expect(request.headers['X-XSRF-TOKEN'], 'tok');
      expect(request.headers['Origin'], 'https://weibo.com');
      expect(form(), {'id': '42'});
    });

    test('like and take back likes of statuses and comments', () async {
      respond = (_) => jsonBody({'ok': 1, 'result': true});
      final api = fakeApi(adapter);

      await api.likeStatus('42', liked: false);
      expect(adapter.requests.last.uri.path, '/ajax/statuses/cancelLike');
      expect(form(), {'id': '42'});

      await api.likeComment('7');
      expect(adapter.requests.last.uri.path, '/ajax/statuses/updateLike');
      expect(form(), {'object_id': '7', 'object_type': 'comment'});

      await api.likeComment('7', liked: false);
      expect(adapter.requests.last.uri.path, '/ajax/statuses/destroyLike');
    });

    test('a like answered ok but without result failed', () {
      // What taking a like back right after giving it gets.
      respond = (_) => jsonBody({'ok': 1, 'result': false});

      expect(
        fakeApi(adapter).likeStatus('42', liked: false),
        throwsA(isA<WeiboApiException>()),
      );
    });

    test('a refusal carries its message', () {
      respond = (_) => jsonBody({
        'ok': 0,
        'message': '微博不存在',
        'msg': '微博不存在',
        'error_code': 20101,
        'error_type': 'toast',
      });

      expect(
        fakeApi(adapter).likeStatus('42'),
        throwsA(
          isA<WeiboApiException>().having((e) => e.message, 'message', '微博不存在'),
        ),
      );
    });

    test('postComment comments, and returns the new comment', () async {
      respond = (_) => jsonBody({
        'ok': 1,
        'msg': '评论成功',
        'data': commentJson(id: 9, text: '好的'),
      });

      final comment = await fakeApi(adapter)
          .postComment(statusId: '42', text: '好的 & 再见');

      expect(adapter.requests.single.uri.path, '/ajax/comments/create');
      expect(form(), {
        'id': '42',
        'comment': '好的 & 再见',
        'is_repost': '0',
        'comment_ori': '0',
        'is_comment': '0',
      });
      expect(comment?.id, '9');
      expect(comment?.text, '好的');
    });

    test('postComment with replyTo replies', () async {
      respond = (_) => jsonBody({'ok': 1, 'data': commentJson(id: 10)});

      await fakeApi(adapter)
          .postComment(statusId: '42', text: '同意', replyTo: '9');

      expect(adapter.requests.single.uri.path, '/ajax/comments/reply');
      expect(form()['id'], '42');
      expect(form()['cid'], '9');
      expect(form()['comment'], '同意');
    });

    test('deleteComment', () async {
      respond = (_) => jsonBody({'ok': 1, 'data': commentJson(id: 9)});

      await fakeApi(adapter).deleteComment('9');

      expect(adapter.requests.single.uri.path, '/ajax/statuses/destroyComment');
      expect(form(), {'cid': '9'});
    });

    test('postStatus posts text and pictures for an audience', () async {
      respond = (_) => jsonBody({
        'ok': 1,
        'msg': '发布成功',
        'data': statusJson(
          id: 77,
          text: '你好',
          extra: {
            'visible': {'type': 1, 'list_id': 0},
          },
        ),
      });

      final status = await fakeApi(adapter).postStatus(
        text: '你好 & #话题#',
        pids: ['p1', 'p2'],
        visibility: StatusVisibility.onlyMe,
      );

      expect(adapter.requests.single.uri.path, '/ajax/statuses/update');
      expect(form(), {
        'content': '你好 & #话题#',
        'pic_id': '[{"type":"pic","pid":"p1"},{"type":"pic","pid":"p2"}]',
        'visible': '1',
      });
      expect(status?.id, '77');
      expect(status?.visibility, StatusVisibility.onlyMe);
    });

    test('postStatus without pictures sends no pic_id', () async {
      respond = (_) => jsonBody({'ok': 1, 'data': statusJson()});

      await fakeApi(adapter).postStatus(text: '只有字');

      expect(form(), {'content': '只有字', 'visible': '0'});
    });

    test('deleteStatus', () async {
      respond = (_) => jsonBody({'ok': 1, 'data': statusJson(id: 77)});

      await fakeApi(adapter).deleteStatus('77');

      expect(adapter.requests.single.uri.path, '/ajax/statuses/destroy');
      expect(form(), {'id': '77'});
    });

    test('uploadPicture sends the bytes to picupload, without XHR headers, '
        'and returns the pid', () async {
      respond = (_) => htmlBody(
        '{"code":"A00006","data":{"count":1,'
        '"pics":{"pic_1":{"width":10,"pid":"0067abc","ret":1}}}}',
      );
      final api = WeiboApi(
        createWeiboDio(
          jar: WeiboCookieJar({
            'weibo.com': {'SUB': 'x', 'XSRF-TOKEN': 'tok'},
          }),
          throttle: RequestThrottle(minInterval: Duration.zero),
        )..httpClientAdapter = adapter,
      );

      final pid = await api.uploadPicture('GIF89a'.codeUnits);

      final request = adapter.requests.single;
      expect(request.method, 'POST');
      expect(request.uri.host, 'picupload.weibo.com');
      expect(request.uri.path, '/interface/pic_upload.php');
      expect(query(), {'app': 'miniblog', 's': 'json', 'p': '1', 'data': '1'});
      expect(adapter.bodies.single, 'GIF89a');
      expect(request.headers['Cookie'], contains('SUB=x'));
      expect(request.headers['Origin'], 'https://weibo.com');
      expect(request.headers.containsKey('X-Requested-With'), isFalse);
      expect(request.headers.containsKey('X-XSRF-TOKEN'), isFalse);
      expect(pid, '0067abc');
    });

    test('uploadPicture fails on an answer without a pid', () {
      respond = (_) => htmlBody('{"code":"A20001","data":{"count":-10}}');

      expect(
        fakeApi(adapter).uploadPicture([1, 2, 3]),
        throwsA(isA<WeiboApiException>()),
      );
    });

    test('an answer that says ok but has an error failed', () {
      respond = (_) => jsonBody({'ok': 1, 'error': '评论太频繁'});

      expect(
        fakeApi(adapter).postComment(statusId: '42', text: '好'),
        throwsA(
          isA<WeiboApiException>().having((e) => e.message, 'message', '评论太频繁'),
        ),
      );
    });
  });

  group('parseUploadedPid', () {
    test('reads either upload answer, after a script prelude', () {
      expect(
        parseUploadedPid(
          '<script>document.domain="sina.com.cn";</script>'
          '{"code":"A00006","data":{"pics":{"pic_1":{"pid":"a1"}}}}',
        ),
        'a1',
      );
      expect(parseUploadedPid('{"ret":true,"pic":{"pid":"b2"}}'), 'b2');
    });

    test('is null for failures and junk', () {
      expect(parseUploadedPid('{"ret":false,"errno":-10}'), isNull);
      expect(parseUploadedPid('{"code":"A20001"}'), isNull);
      expect(parseUploadedPid('<html>'), isNull);
      expect(parseUploadedPid('{oops'), isNull);
    });
  });
}

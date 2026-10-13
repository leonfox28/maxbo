import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/weibo_exception.dart';
import 'package:maxbo/data/models/comment.dart';

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

  test('fetchHotSearch reads the list', () async {
    respond = (_) =>
        jsonBody(hotSearchJson(realtime: [hotItemJson('第一条', realpos: 1)]));

    final hot = await fakeApi(adapter).fetchHotSearch();

    expect(adapter.requests.single.uri.path, '/ajax/side/hotSearch');
    expect(hot.items.single.word, '第一条');
  });
}

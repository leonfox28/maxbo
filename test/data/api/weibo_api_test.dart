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
}

import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/cookie_jar.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_dio.dart';
import 'package:maxbo/core/net/weibo_exception.dart';
import 'package:maxbo/core/session/session_controller.dart';
import 'package:maxbo/core/session/session_storage.dart';
import 'package:maxbo/data/models/account.dart';
import 'package:maxbo/data/models/comment.dart';
import 'package:maxbo/features/comments/comments_controller.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

const CommentsQuery _query = (
  statusId: '42',
  authorId: '1001',
  order: CommentOrder.hot,
);

void main() {
  late FakeAdapter adapter;
  late FutureOr<ResponseBody> Function(RequestOptions) respond;
  late ProviderContainer c;

  setUp(() {
    adapter = FakeAdapter((o) => respond(o));
    c = ProviderContainer(
      retry: noRetry,
      overrides: [
        sessionStorageProvider.overrideWithValue(InMemorySessionStorage()),
        cookieBridgeProvider.overrideWithValue(FakeCookieBridge()),
        sessionRenewerProvider.overrideWithValue(FakeSessionRenewer()),
        restoredSessionProvider.overrideWithValue(
          const StoredSession(
            account: Account(uid: '7'),
            cookies: {
              'weibo.com': {'SUB': 'x'},
            },
          ),
        ),
        weiboDioFactoryProvider.overrideWithValue(
          (WeiboCookieJar jar) => createWeiboDio(
            jar: jar,
            throttle: RequestThrottle(minInterval: Duration.zero),
          )..httpClientAdapter = adapter,
        ),
      ],
    );
    addTearDown(c.dispose);
  });

  List<String> ids() => [
    for (final comment in c.read(commentsProvider(_query)).requireValue.items)
      comment.id,
  ];

  test('loads more from the cursor, skips repeats, stops at the end', () async {
    c.listen(commentsProvider(_query), (_, _) {});
    respond = (_) => jsonBody(
      commentsPageJson([commentJson(id: 1), commentJson(id: 2)], maxId: 50),
    );
    await c.read(commentsProvider(_query).future);
    expect(ids(), ['1', '2']);

    respond = (_) =>
        jsonBody(commentsPageJson([commentJson(id: 2), commentJson(id: 3)]));
    await c.read(commentsProvider(_query).notifier).loadMore();

    expect(adapter.requests.last.uri.queryParameters['max_id'], '50');
    expect(ids(), ['1', '2', '3']);
    final state = c.read(commentsProvider(_query)).requireValue;
    expect(state.hasMore, isFalse);

    await c.read(commentsProvider(_query).notifier).loadMore();
    expect(adapter.requests, hasLength(2));
  });

  test('keeps the list when loading more fails, and can retry', () async {
    c.listen(commentsProvider(_query), (_, _) {});
    respond = (_) =>
        jsonBody(commentsPageJson([commentJson(id: 1)], maxId: 50));
    await c.read(commentsProvider(_query).future);

    respond = (_) => jsonBody({'ok': 0, 'message': '出错了'});
    await c.read(commentsProvider(_query).notifier).loadMore();
    var state = c.read(commentsProvider(_query)).requireValue;
    expect(state.items, hasLength(1));
    expect(state.loadMoreError, isA<WeiboApiException>());
    expect(state.loadMoreError?.message, '出错了');

    respond = (_) => jsonBody(commentsPageJson([commentJson(id: 2)]));
    await c.read(commentsProvider(_query).notifier).loadMore();
    state = c.read(commentsProvider(_query)).requireValue;
    expect(ids(), ['1', '2']);
    expect(state.loadMoreError, isNull);
  });

  test('drops a page that arrives after a refresh', () async {
    c.listen(commentsProvider(_query), (_, _) {});
    respond = (_) =>
        jsonBody(commentsPageJson([commentJson(id: 1)], maxId: 50));
    await c.read(commentsProvider(_query).future);

    final slowPage = Completer<ResponseBody>();
    respond = (_) => slowPage.future;
    final loading = c.read(commentsProvider(_query).notifier).loadMore();

    respond = (_) => jsonBody(commentsPageJson([commentJson(id: 9)]));
    await c.refresh(commentsProvider(_query).future);
    slowPage.complete(jsonBody(commentsPageJson([commentJson(id: 2)])));
    await loading;

    expect(ids(), ['9']);
  });

  test('the user\'s comments go atop the list, deleted ones out', () async {
    c.listen(commentsProvider(_query), (_, _) {});
    respond = (_) => jsonBody(
      commentsPageJson([commentJson(id: 1), commentJson(id: 2)], total: 10),
    );
    await c.read(commentsProvider(_query).future);
    final list = c.read(commentsProvider(_query).notifier);

    list.prepend(Comment.tryFromJson(commentJson(id: 3))!);
    expect(ids(), ['3', '1', '2']);
    expect(c.read(commentsProvider(_query)).requireValue.total, 11);

    list.remove('1');
    list.remove('404');
    expect(ids(), ['3', '2']);
    expect(c.read(commentsProvider(_query)).requireValue.total, 10);

    list.replace('2', (comment) => comment.copyWith(replyCount: 5));
    final items = c.read(commentsProvider(_query)).requireValue.items;
    expect(items.last.replyCount, 5);
  });

  test('a comment added while a page loads stays', () async {
    c.listen(commentsProvider(_query), (_, _) {});
    respond = (_) =>
        jsonBody(commentsPageJson([commentJson(id: 1)], maxId: 50));
    await c.read(commentsProvider(_query).future);

    final slowPage = Completer<ResponseBody>();
    respond = (_) => slowPage.future;
    final list = c.read(commentsProvider(_query).notifier);
    final loading = list.loadMore();

    list.prepend(Comment.tryFromJson(commentJson(id: 3))!);
    slowPage.complete(jsonBody(commentsPageJson([commentJson(id: 2)])));
    await loading;

    expect(ids(), ['3', '1', '2']);
    expect(c.read(commentsProvider(_query)).requireValue.loadingMore, isFalse);
  });
}

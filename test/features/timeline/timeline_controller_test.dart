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
import 'package:maxbo/data/models/status.dart';
import 'package:maxbo/features/timeline/timeline_controller.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

const _session = StoredSession(
  account: Account(uid: '42'),
  cookies: {
    'weibo.com': {'SUB': 'x'},
  },
);

void main() {
  late FakeAdapter adapter;
  late FutureOr<ResponseBody> Function(RequestOptions) respond;

  ProviderContainer container({StoredSession? restored = _session}) {
    adapter = FakeAdapter((o) => respond(o));
    final c = ProviderContainer(
      retry: noRetry,
      overrides: [
        sessionStorageProvider.overrideWithValue(InMemorySessionStorage()),
        cookieBridgeProvider.overrideWithValue(FakeCookieBridge()),
        sessionRenewerProvider.overrideWithValue(FakeSessionRenewer()),
        restoredSessionProvider.overrideWithValue(restored),
        weiboDioFactoryProvider.overrideWithValue(
          (WeiboCookieJar jar) => createWeiboDio(
            jar: jar,
            throttle: RequestThrottle(minInterval: Duration.zero),
          )..httpClientAdapter = adapter,
        ),
      ],
    );
    addTearDown(c.dispose);
    return c;
  }

  List<String> ids(ProviderContainer c) => [
    for (final s in c.read(timelineProvider).requireValue.statuses) s.id,
  ];

  test('loads the newest page of 最新微博', () async {
    respond = (_) =>
        jsonBody(feedJson([statusJson(id: 1), statusJson(id: 2)], maxId: 100));
    final c = container();

    final timeline = await c.read(timelineProvider.future);

    expect(timeline.statuses.map((s) => s.id), ['1', '2']);
    expect(timeline.hasMore, isTrue);
    final query = adapter.requests.single.uri.queryParameters;
    expect(adapter.requests.single.uri.path, '/ajax/feed/friendstimeline');
    expect(query['list_id'], '1100042');
    expect(query['since_id'], '0');
    expect(query.containsKey('max_id'), isFalse);
  });

  test('signed out: empty, without requests', () async {
    respond = (_) => throw StateError('no request expected');
    final c = container(restored: null);

    final timeline = await c.read(timelineProvider.future);

    expect(timeline.statuses, isEmpty);
    expect(adapter.requests, isEmpty);
  });

  test(
    'loadMore continues from max_id, skips repeats and stops at the end',
    () async {
      respond = (o) => jsonBody(
        o.uri.queryParameters['max_id'] == null
            ? feedJson([statusJson(id: 3), statusJson(id: 2)], maxId: 100)
            : feedJson([statusJson(id: 2), statusJson(id: 1)]),
      );
      final c = container();
      await c.read(timelineProvider.future);

      await c.read(timelineProvider.notifier).loadMore();

      expect(ids(c), ['3', '2', '1']);
      expect(adapter.requests.last.uri.queryParameters['max_id'], '100');
      expect(c.read(timelineProvider).requireValue.hasMore, isFalse);

      await c.read(timelineProvider.notifier).loadMore();
      expect(adapter.requests, hasLength(2));
    },
  );

  test('a failed loadMore is kept in state until retried', () async {
    var fail = true;
    respond = (o) {
      if (o.uri.queryParameters['max_id'] == null) {
        return jsonBody(feedJson([statusJson(id: 2)], maxId: 100));
      }
      return fail
          ? jsonBody({'ok': 0, 'message': '服务繁忙'})
          : jsonBody(feedJson([statusJson(id: 1)]));
    };
    final c = container();
    await c.read(timelineProvider.future);

    await c.read(timelineProvider.notifier).loadMore();

    var timeline = c.read(timelineProvider).requireValue;
    expect(timeline.loadMoreError?.message, '服务繁忙');
    expect(timeline.loadingMore, isFalse);
    expect(ids(c), ['2']);

    fail = false;
    await c.read(timelineProvider.notifier).loadMore();

    timeline = c.read(timelineProvider).requireValue;
    expect(timeline.loadMoreError, isNull);
    expect(ids(c), ['2', '1']);
  });

  test(
    'refresh replaces the list; a failed refresh keeps it and throws',
    () async {
      var page = [statusJson(id: 1)];
      respond = (_) => jsonBody(feedJson(page));
      final c = container();
      await c.read(timelineProvider.future);

      page = [statusJson(id: 2), statusJson(id: 1)];
      await c.read(timelineProvider.notifier).refresh();
      expect(ids(c), ['2', '1']);

      respond = (_) => jsonBody({'ok': 0, 'message': '服务繁忙'}, status: 500);
      await expectLater(
        c.read(timelineProvider.notifier).refresh(),
        throwsA(isA<WeiboApiException>()),
      );
      expect(ids(c), ['2', '1']);
    },
  );

  test('a page loaded before a refresh lands is dropped', () async {
    final olderPage = Completer<ResponseBody>();
    respond = (o) => o.uri.queryParameters['max_id'] == null
        ? jsonBody(feedJson([statusJson(id: 5)], maxId: 100))
        : olderPage.future;
    final c = container();
    await c.read(timelineProvider.future);

    final loading = c.read(timelineProvider.notifier).loadMore();
    expect(c.read(timelineProvider).requireValue.loadingMore, isTrue);
    await c.read(timelineProvider.notifier).refresh();
    olderPage.complete(jsonBody(feedJson([statusJson(id: 1)])));
    await loading;

    expect(ids(c), ['5']);
    expect(c.read(timelineProvider).requireValue.loadingMore, isFalse);
  });

  test('a session rejected while loading more signs out', () async {
    respond = (o) => o.uri.queryParameters['max_id'] == null
        ? jsonBody(feedJson([statusJson(id: 2)], maxId: 100))
        : jsonBody({'ok': -100, 'url': 'https://passport.weibo.com/'});
    final c = container();
    await c.read(timelineProvider.future);

    await c.read(timelineProvider.notifier).loadMore();
    await c.pump();

    expect(
      c.read(sessionProvider),
      isA<SignedOut>().having((s) => s.expired, 'expired', isTrue),
    );
    expect((await c.read(timelineProvider.future)).statuses, isEmpty);
  });

  test('posts the user writes or deletes go in and out', () async {
    respond = (_) => jsonBody(feedJson([statusJson(id: 1), statusJson(id: 2)]));
    final c = container();
    await c.read(timelineProvider.future);
    final timeline = c.read(timelineProvider.notifier);

    timeline.prepend(Status.tryFromJson(statusJson(id: 3))!);
    expect(ids(c), ['3', '1', '2']);
    timeline.remove('1');
    expect(ids(c), ['3', '2']);
  });

  test('a page loaded while the user posts follows the post', () async {
    final olderPage = Completer<ResponseBody>();
    respond = (o) => o.uri.queryParameters['max_id'] == null
        ? jsonBody(feedJson([statusJson(id: 5)], maxId: 100))
        : olderPage.future;
    final c = container();
    await c.read(timelineProvider.future);

    final loading = c.read(timelineProvider.notifier).loadMore();
    c
        .read(timelineProvider.notifier)
        .prepend(Status.tryFromJson(statusJson(id: 9))!);
    olderPage.complete(jsonBody(feedJson([statusJson(id: 1)])));
    await loading;

    expect(ids(c), ['9', '5', '1']);
    expect(c.read(timelineProvider).requireValue.loadingMore, isFalse);
  });
}

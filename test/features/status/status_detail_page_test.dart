import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/cookie_jar.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_dio.dart';
import 'package:maxbo/core/session/session_controller.dart';
import 'package:maxbo/core/session/session_storage.dart';
import 'package:maxbo/data/models/account.dart';
import 'package:maxbo/data/models/status.dart';
import 'package:maxbo/features/status/status_detail_page.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;

  ResponseBody respond(RequestOptions o) => switch (o.uri.path) {
    '/ajax/statuses/show' => jsonBody({
      'ok': 1,
      ...statusJson(id: 1, text: '完整的正文'),
    }),
    '/ajax/statuses/buildComments' => jsonBody(
      commentsPageJson([
        commentJson(
          text: o.uri.queryParameters['flow'] == '1' ? '最新的评论' : '最热的评论',
        ),
      ]),
    ),
    '/ajax/statuses/repostTimeline' => jsonBody(
      repostsPageJson([statusJson(id: 8, text: '转发了这条')]),
    ),
    final path => throw StateError('unexpected request to $path'),
  };

  int requestsTo(String path) =>
      adapter.requests.where((r) => r.uri.path == path).length;

  Future<void> pumpPage(WidgetTester tester) async {
    adapter = FakeAdapter(respond);
    await tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: [
          sessionStorageProvider.overrideWithValue(InMemorySessionStorage()),
          cookieBridgeProvider.overrideWithValue(FakeCookieBridge()),
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
        child: MaterialApp(
          home: StatusDetailPage(
            id: '1',
            initial: Status.tryFromJson(
              statusJson(id: 1, text: '截断的正文<span class="expand">展开</span>'),
            ),
          ),
        ),
      ),
    );
  }

  testWidgets('shows the status it was given, then its full text', (
    tester,
  ) async {
    await pumpPage(tester);
    expect(find.textContaining('截断的正文'), findsOneWidget);

    await tester.pumpAndSettle();

    expect(find.text('完整的正文'), findsOneWidget);
    expect(find.text('评论 1.2万'), findsOneWidget);
    expect(find.text('转发 3'), findsOneWidget);
    expect(find.text('最热的评论'), findsOneWidget);
    expect(adapter.requests.first.uri.queryParameters['isGetLongText'], 'true');
    // Reposts wait until their tab is opened.
    expect(requestsTo('/ajax/statuses/repostTimeline'), 0);
  });

  testWidgets('loads each list once, however often it is shown', (
    tester,
  ) async {
    await pumpPage(tester);
    await tester.pumpAndSettle();

    await tester.tap(find.text('按时间'));
    await tester.pumpAndSettle();
    expect(find.text('最新的评论'), findsOneWidget);

    await tester.tap(find.text('按热度'));
    await tester.pumpAndSettle();
    expect(find.text('最热的评论'), findsOneWidget);

    await tester.tap(find.text('转发 3'));
    await tester.pumpAndSettle();
    expect(find.text('转发了这条'), findsOneWidget);
    expect(find.text('按热度'), findsNothing);

    await tester.tap(find.text('评论 1.2万'));
    await tester.pumpAndSettle();
    expect(find.text('最热的评论'), findsOneWidget);

    expect(requestsTo('/ajax/statuses/show'), 1);
    expect(requestsTo('/ajax/statuses/buildComments'), 2);
    expect(requestsTo('/ajax/statuses/repostTimeline'), 1);
  });
}

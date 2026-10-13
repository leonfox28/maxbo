import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/app.dart';
import 'package:maxbo/core/net/cookie_jar.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_dio.dart';
import 'package:maxbo/core/session/session_controller.dart';
import 'package:maxbo/core/session/session_storage.dart';
import 'package:maxbo/data/models/account.dart';

import 'support/fakes.dart';
import 'support/statuses.dart';

void main() {
  Widget app({StoredSession? restored, FakeCookieBridge? bridge}) =>
      ProviderScope(
        retry: noRetry,
        overrides: [
          sessionStorageProvider.overrideWithValue(InMemorySessionStorage()),
          cookieBridgeProvider.overrideWithValue(bridge ?? FakeCookieBridge()),
          restoredSessionProvider.overrideWithValue(restored),
          weiboDioFactoryProvider.overrideWithValue(
            (WeiboCookieJar jar) =>
                createWeiboDio(
                    jar: jar,
                    throttle: RequestThrottle(minInterval: Duration.zero),
                  )
                  ..httpClientAdapter = FakeAdapter(
                    (_) => jsonBody(feedJson([statusJson(text: '首页的一条微博')])),
                  ),
          ),
        ],
        child: const MaxboApp(),
      );

  testWidgets('signed out: tabs prompt to log in', (tester) async {
    await tester.pumpWidget(app());

    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.text('登录后即可浏览关注的人的微博'), findsOneWidget);

    await tester.tap(find.text('热搜'));
    await tester.pumpAndSettle();
    expect(find.text('热搜榜将在 M4 实现'), findsOneWidget);
  });

  testWidgets('signed in: Me shows the account and can log out', (
    tester,
  ) async {
    final bridge = FakeCookieBridge();
    await tester.pumpWidget(
      app(
        bridge: bridge,
        restored: const StoredSession(
          account: Account(uid: '42', screenName: '测试用户'),
          cookies: {
            'weibo.com': {'SUB': 'x'},
          },
        ),
      ),
    );

    await tester.pumpAndSettle();
    expect(find.text('首页的一条微博'), findsOneWidget);

    await tester.tap(find.text('我'));
    await tester.pumpAndSettle();
    expect(find.text('测试用户'), findsOneWidget);
    expect(find.text('UID 42'), findsOneWidget);

    await tester.tap(find.text('退出登录'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('退出'));
    await tester.pumpAndSettle();

    expect(find.text('登录后即可浏览关注的人的微博'), findsOneWidget);
    expect(bridge.cleared, isTrue);
  });
}

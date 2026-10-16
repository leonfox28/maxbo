import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/app.dart';
import 'package:maxbo/core/net/cookie_jar.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_dio.dart';
import 'package:maxbo/core/session/session_controller.dart';
import 'package:maxbo/core/session/session_storage.dart';
import 'package:maxbo/core/settings/app_settings.dart';
import 'package:maxbo/core/theme.dart';
import 'package:maxbo/data/models/account.dart';
import 'package:maxbo/widgets/adaptive_layout.dart';
import 'package:maxbo/widgets/floating_pill.dart';
import 'package:maxbo/widgets/status_card/status_card.dart';

import 'support/fakes.dart';
import 'support/statuses.dart';

void main() {
  Widget app({
    StoredSession? restored,
    FakeCookieBridge? bridge,
    AppSettings settings = const AppSettings(),
    List<Map<String, Object?>>? feed,
    FakeAdapter? adapter,
  }) => ProviderScope(
    retry: noRetry,
    overrides: [
      sessionStorageProvider.overrideWithValue(InMemorySessionStorage()),
      cookieBridgeProvider.overrideWithValue(bridge ?? FakeCookieBridge()),
      restoredSessionProvider.overrideWithValue(restored),
      initialSettingsProvider.overrideWithValue(settings),
      settingsStorageProvider.overrideWithValue(InMemorySettingsStorage()),
      weiboDioFactoryProvider.overrideWithValue(
        (WeiboCookieJar jar) =>
            createWeiboDio(
                jar: jar,
                throttle: RequestThrottle(minInterval: Duration.zero),
              )
              ..httpClientAdapter =
                  adapter ??
                  FakeAdapter(
                    (_) => jsonBody(
                      feedJson(feed ?? [statusJson(text: '首页的一条微博')]),
                    ),
                  ),
      ),
    ],
    child: const MaxboApp(),
  );

  testWidgets('signed out: tabs prompt to log in', (tester) async {
    await tester.pumpWidget(app());

    expect(find.byTooltip('首页'), findsOneWidget);
    expect(find.text('登录后即可浏览关注的人的微博'), findsOneWidget);

    await tester.tap(find.byTooltip('热搜'));
    await tester.pumpAndSettle();
    // The home tab's prompt is offstage now.
    expect(find.text('登录后即可浏览关注的人的微博'), findsOneWidget);
    expect(find.text('登录微博'), findsOneWidget);
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

    await tester.tap(find.byTooltip('我'));
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

  testWidgets('the tab bar hides while scrolling down, returns going up', (
    tester,
  ) async {
    await tester.pumpWidget(
      app(
        restored: const StoredSession(
          account: Account(uid: '42'),
          cookies: {
            'weibo.com': {'SUB': 'x'},
          },
        ),
        feed: [
          for (var i = 0; i < 20; i++)
            statusJson(id: 5000000000000001 + i, text: '第 $i 条'),
        ],
      ),
    );
    await tester.pumpAndSettle();
    Offset navOffset() =>
        tester.widget<AnimatedSlide>(find.byType(AnimatedSlide)).offset;
    expect(navOffset(), Offset.zero);

    final gesture = await tester.startGesture(
      tester.getCenter(find.byType(ListView)),
    );
    for (var i = 0; i < 10; i++) {
      await gesture.moveBy(const Offset(0, -30));
      await tester.pump();
    }
    expect(navOffset(), isNot(Offset.zero));
    // A few pixels back, as when lifting a finger, leave it hidden.
    await gesture.moveBy(const Offset(0, 10));
    await gesture.up();
    await tester.pumpAndSettle();
    expect(navOffset(), isNot(Offset.zero));

    await tester.drag(find.byType(ListView), const Offset(0, 100));
    await tester.pumpAndSettle();
    expect(navOffset(), Offset.zero);
  });

  testWidgets('tapping the open tab scrolls to the top, then refreshes', (
    tester,
  ) async {
    final adapter = FakeAdapter(
      (_) => jsonBody(
        feedJson([
          for (var i = 0; i < 20; i++)
            statusJson(id: 5000000000000001 + i, text: '第 $i 条'),
        ]),
      ),
    );
    await tester.pumpWidget(
      app(
        adapter: adapter,
        restored: const StoredSession(
          account: Account(uid: '42'),
          cookies: {
            'weibo.com': {'SUB': 'x'},
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    int feedRequests() => adapter.requests
        .where((o) => o.uri.path == '/ajax/feed/friendstimeline')
        .length;
    expect(feedRequests(), 1);

    // Scrolled without dragging, which would hide the tab bar.
    tester
        .state<ScrollableState>(find.byType(Scrollable).first)
        .position
        .jumpTo(1500);
    await tester.pumpAndSettle();
    expect(find.text('第 0 条'), findsNothing);

    await tester.tap(find.byTooltip('首页'));
    await tester.pumpAndSettle();
    expect(find.text('第 0 条'), findsOneWidget);
    expect(feedRequests(), 1);

    await tester.tap(find.byTooltip('首页'));
    await tester.pumpAndSettle();
    expect(feedRequests(), 2);
  });

  testWidgets('applies the saved theme; settings open from Me', (tester) async {
    await tester.pumpWidget(
      app(settings: const AppSettings(themeMode: ThemeMode.dark)),
    );
    final material = tester.widget<MaterialApp>(find.byType(MaterialApp));
    expect(material.themeMode, ThemeMode.dark);

    await tester.tap(find.byTooltip('我'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('关于 maxbo'));
    await tester.pumpAndSettle();
    expect(find.textContaining('maxbo 是非官方客户端'), findsOneWidget);
  });

  testWidgets('unread notifications turn the tab icon red until all seen', (
    tester,
  ) async {
    final adapter = FakeAdapter(
      (o) => switch (o.uri.path) {
        '/2/remind/push_count.json' => jsonBody({
          'cmt': 1,
          'attitude': 1,
          'dm_unread': 3,
        }),
        '/ajax/message/cmt' => jsonBody(
          noticePageJson('comments', [commentNoticeJson()]),
        ),
        '/ajax/message/attitudes' => jsonBody(
          noticePageJson('attitudes', [likeNoticeJson()]),
        ),
        _ => jsonBody(feedJson([statusJson(text: '首页的一条微博')])),
      },
    );
    await tester.pumpWidget(
      app(
        adapter: adapter,
        restored: const StoredSession(
          account: Account(uid: '42'),
          cookies: {
            'weibo.com': {'SUB': 'x'},
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    int checks() => adapter.requests
        .where((o) => o.uri.path == '/2/remind/push_count.json')
        .length;
    Color? bellColor() =>
        tester.widget<Icon>(find.byIcon(Icons.notifications_outlined)).color;
    expect(checks(), 1);
    expect(bellColor(), unreadColor);

    // Coming back to the app checks again, but not within a minute.
    for (final state in [
      AppLifecycleState.inactive,
      AppLifecycleState.hidden,
      AppLifecycleState.paused,
      AppLifecycleState.hidden,
      AppLifecycleState.inactive,
      AppLifecycleState.resumed,
    ]) {
      tester.binding.handleAppLifecycleStateChanged(state);
    }
    await tester.pumpAndSettle();
    expect(checks(), 1);

    // The comments tab opens first; the likes are still unread.
    await tester.tap(find.byTooltip('通知'));
    await tester.pumpAndSettle();
    expect(find.text('一条评论通知'), findsOneWidget);
    await tester.tap(find.byTooltip('首页'));
    await tester.pumpAndSettle();
    expect(bellColor(), unreadColor);

    await tester.tap(find.byTooltip('通知'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.descendant(of: find.byType(TabBar), matching: find.text('赞')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('首页'));
    await tester.pumpAndSettle();
    expect(bellColor(), isNot(unreadColor));
  });

  testWidgets('a wide window has a rail; folding and unfolding keep the tabs', (
    tester,
  ) async {
    void resize(Size size) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
    }

    addTearDown(tester.view.reset);
    const phone = Size(400, 800);
    const unfolded = Size(1200, 900);
    resize(phone);
    await tester.pumpWidget(
      app(
        adapter: FakeAdapter(
          (o) => switch (o.uri.path) {
            '/2/remind/push_count.json' => jsonBody({'cmt': 1}),
            _ => jsonBody(
              feedJson([
                for (var i = 0; i < 20; i++)
                  statusJson(id: 5000000000000001 + i, text: '第 $i 条'),
              ]),
            ),
          },
        ),
        restored: const StoredSession(
          account: Account(uid: '42'),
          cookies: {
            'weibo.com': {'SUB': 'x'},
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    final timeline = find.descendant(
      of: find.byType(ListView),
      matching: find.byType(Scrollable),
    );
    double scrolled() =>
        tester.state<ScrollableState>(timeline.first).position.pixels;
    Offset navOffset() =>
        tester.widget<AnimatedSlide>(find.byType(AnimatedSlide)).offset;

    // Scrolling down hides the pill.
    await tester.drag(timeline.first, const Offset(0, -300));
    await tester.pumpAndSettle();
    final position = scrolled();
    expect(position, greaterThan(0));
    expect(navOffset(), isNot(Offset.zero));

    resize(unfolded);
    await tester.pumpAndSettle();
    expect(find.byType(NavigationRail), findsOneWidget);
    expect(find.byType(FloatingPill), findsNothing);
    expect(scrolled(), position);
    // The cards keep to a column centred right of the rail.
    final card = tester.getRect(find.byType(StatusCard).first);
    final tabsLeft = tester.getTopRight(find.byType(NavigationRail)).dx + 1;
    expect(card.width, readingWidth);
    expect(card.center.dx, closeTo((tabsLeft + unfolded.width) / 2, 0.01));
    // Unread notifications show on the rail as on the pill.
    expect(
      tester.widget<Icon>(find.byIcon(Icons.notifications_outlined)).color,
      unreadColor,
    );

    // Folded again, the pill is back where the list was left.
    resize(phone);
    await tester.pumpAndSettle();
    expect(find.byType(NavigationRail), findsNothing);
    expect(navOffset(), Offset.zero);
    expect(scrolled(), position);

    // The rail switches tabs, and its open tab scrolls back to the top.
    resize(unfolded);
    await tester.pumpAndSettle();
    final rail = find.byType(NavigationRail);
    await tester.tap(find.descendant(of: rail, matching: find.text('热搜')));
    await tester.pumpAndSettle();
    expect(find.byType(StatusCard), findsNothing);
    await tester.tap(find.descendant(of: rail, matching: find.text('首页')));
    await tester.pumpAndSettle();
    expect(scrolled(), position);
    await tester.tap(find.descendant(of: rail, matching: find.text('首页')));
    await tester.pumpAndSettle();
    expect(scrolled(), 0);
  });
}

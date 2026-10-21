import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
import 'package:maxbo/features/shell/home_shell.dart';
import 'package:maxbo/widgets/adaptive_layout.dart';
import 'package:maxbo/widgets/floating_pill.dart';
import 'package:maxbo/widgets/status_card/status_card.dart';

import 'support/fakes.dart';
import 'support/statuses.dart';

void main() {
  final pillSlide = find.ancestor(
    of: find.byType(FloatingPill),
    matching: find.byType(AnimatedSlide),
  );
  final composeSlide = find.ancestor(
    of: find.byType(FloatingActionButton),
    matching: find.byType(AnimatedSlide),
  );

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
      sessionRenewerProvider.overrideWithValue(FakeSessionRenewer()),
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

    await tester.tap(find.byTooltip('搜索'));
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
    Offset navOffset() => tester.widget<AnimatedSlide>(pillSlide).offset;
    expect(navOffset(), Offset.zero);

    final gesture = await tester.startGesture(
      tester.getCenter(find.byType(ListView)),
    );
    for (var i = 0; i < 10; i++) {
      await gesture.moveBy(const Offset(0, -30));
      await tester.pump();
    }
    expect(navOffset(), isNot(Offset.zero));
    expect(
      tester.widget<AnimatedSlide>(composeSlide).offset,
      isNot(Offset.zero),
    );
    // A few pixels back, as when lifting a finger, leave it hidden.
    await gesture.moveBy(const Offset(0, 10));
    await gesture.up();
    await tester.pumpAndSettle();
    expect(navOffset(), isNot(Offset.zero));

    await tester.drag(find.byType(ListView), const Offset(0, 100));
    await tester.pumpAndSettle();
    expect(navOffset(), Offset.zero);
  });

  testWidgets('the home tab has a button to write a post, when signed in', (
    tester,
  ) async {
    await tester.pumpWidget(app());
    expect(find.byTooltip('发微博'), findsNothing);

    await tester.pumpWidget(
      app(
        restored: const StoredSession(
          account: Account(uid: '42'),
          cookies: {
            'weibo.com': {'SUB': 'x'},
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('搜索'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('发微博'), findsNothing);

    await tester.tap(find.byTooltip('首页'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('发微博'));
    await tester.pumpAndSettle();
    expect(find.text('分享新鲜事…'), findsOneWidget);
  });

  testWidgets('the user deletes their own post from the home tab', (
    tester,
  ) async {
    final adapter = FakeAdapter(
      (o) => switch (o.uri.path) {
        '/ajax/statuses/destroy' => jsonBody({'ok': 1, ...statusJson(id: 2)}),
        _ => jsonBody(
          feedJson([
            statusJson(id: 1, text: '别人的微博'),
            statusJson(id: 2, text: '我的微博', user: userJson(id: 42)),
          ]),
        ),
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
    expect(find.byTooltip('更多'), findsOneWidget);

    await tester.tap(find.byTooltip('更多'));
    await tester.pumpAndSettle();
    // Over the tab bar, not under it.
    expect(
      find.descendant(of: find.byType(HomeShell), matching: find.text('删除')),
      findsNothing,
    );
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();

    expect(adapter.requests.last.uri.path, '/ajax/statuses/destroy');
    expect(find.text('我的微博'), findsNothing);
    expect(find.text('别人的微博'), findsOneWidget);
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

  // Both need pages with the platform's transitions, which go_router's
  // `builder:` doesn't give maxbo (see `_page()` in router.dart).
  testWidgets('pages follow the predictive back gesture', (tester) async {
    Future<void> backGesture(String method, [Map<String, Object?>? args]) =>
        tester.binding.defaultBinaryMessenger.handlePlatformMessage(
          'flutter/backgesture',
          const StandardMethodCodec().encodeMethodCall(
            MethodCall(method, args),
          ),
          (_) {},
        );

    await tester.pumpWidget(app());
    await tester.tap(find.byTooltip('我'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();

    final section = find.text('外观');
    final before = tester.getTopLeft(section);
    await backGesture('startBackGesture', {
      'touchOffset': [5.0, 300.0],
      'progress': 0.0,
      'swipeEdge': 0,
    });
    await backGesture('updateBackGestureProgress', {
      'x': 100.0,
      'y': 300.0,
      'progress': 0.35,
      'swipeEdge': 0,
    });
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(section), isNot(before));

    await backGesture('commitBackGesture');
    await tester.pumpAndSettle();
    expect(section, findsNothing);
    expect(find.text('设置'), findsOneWidget);
  });

  testWidgets('pages swipe back from the edge', (tester) async {
    await tester.pumpWidget(app());
    await tester.tap(find.byTooltip('我'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();

    final section = find.text('外观');
    final before = tester.getTopLeft(section);
    final swipe = await tester.startGesture(const Offset(5, 300));
    for (var i = 0; i < 10; i++) {
      await swipe.moveBy(const Offset(50, 0));
      await tester.pump(const Duration(milliseconds: 16));
    }
    expect(tester.getTopLeft(section).dx, greaterThan(before.dx));

    await swipe.up();
    await tester.pumpAndSettle();
    expect(section, findsNothing);
    expect(find.text('设置'), findsOneWidget);
  }, variant: TargetPlatformVariant.only(TargetPlatform.iOS));

  testWidgets('the text size applies everywhere, even on a narrow phone', (
    tester,
  ) async {
    // Overflowing text, as from a row too narrow for it, fails the test.
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final adapter = FakeAdapter(
      (o) => switch (o.uri.path) {
        '/2/remind/push_count.json' => jsonBody({'cmt': 1}),
        '/ajax/side/hotSearch' => jsonBody(
          hotSearchJson(
            realtime: [
              hotItemJson('一条很长很长很长很长很长的热搜', realpos: 1, icon: '热'),
              hotItemJson('付费的一条', icon: '首发', smallIcon: '商', isAd: 1),
            ],
          ),
        ),
        '/ajax/message/cmt' => jsonBody(
          noticePageJson('comments', [commentNoticeJson()]),
        ),
        '/ajax/statuses/show' => jsonBody(statusJson(text: '首页的一条微博')),
        '/ajax/statuses/buildComments' => jsonBody(
          commentsPageJson([commentJson(text: '一条评论')]),
        ),
        _ => jsonBody(
          feedJson([
            statusJson(
              text: '首页的一条微博',
              user: userJson(name: '一个名字非常非常长的用户'),
              retweeted: statusJson(
                id: 5000000000000002,
                text: '被转发的微博',
                pics: [picJson('p1'), picJson('p2')],
              ),
            ),
          ]),
        ),
      },
    );
    await tester.pumpWidget(
      app(
        adapter: adapter,
        settings: const AppSettings(textSize: TextSize.extraLarge),
        restored: const StoredSession(
          account: Account(uid: '42', screenName: '测试用户'),
          cookies: {
            'weibo.com': {'SUB': 'x'},
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    final post = find.text('首页的一条微博');
    expect(
      MediaQuery.textScalerOf(tester.element(post)).scale(10),
      closeTo(13, 1e-9),
    );

    await tester.tap(post);
    await tester.pumpAndSettle();
    expect(find.text('一条评论'), findsOneWidget);
    await tester.tap(find.byTooltip('返回'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('搜索'));
    await tester.pumpAndSettle();
    expect(find.text('一条很长很长很长很长很长的热搜'), findsOneWidget);
    await tester.tap(find.byTooltip('通知'));
    await tester.pumpAndSettle();
    expect(find.text('一条评论通知'), findsOneWidget);
    await tester.tap(find.byTooltip('我'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();
    // Dialogs too.
    await tester.tap(find.text('主题'));
    await tester.pumpAndSettle();
    final option = find.descendant(
      of: find.byType(SimpleDialog),
      matching: find.text('跟随系统'),
    );
    expect(
      MediaQuery.textScalerOf(tester.element(option)).scale(10),
      closeTo(13, 1e-9),
    );
    await tester.tap(find.text('浅色'));
    await tester.pumpAndSettle();

    // A step down on the slider page applies at once.
    await tester.tap(find.text('字体大小'));
    await tester.pumpAndSettle();
    final sample = find.textContaining('拖动下面的滑块');
    expect(sample, findsOneWidget);
    await tester.tap(find.byTooltip('调小'));
    await tester.pumpAndSettle();
    expect(
      MediaQuery.textScalerOf(tester.element(sample)).scale(10),
      closeTo(11.5, 1e-9),
    );
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

  testWidgets('a wide window has the pill in its corner; folding keeps tabs', (
    tester,
  ) async {
    void resize(Size size) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
    }

    addTearDown(tester.view.reset);
    const phone = Size(400, 800);
    const unfolded = Size(1280, 800);
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
    Offset navOffset() => tester.widget<AnimatedSlide>(pillSlide).offset;
    Rect pill() => tester.getRect(
      find.descendant(
        of: find.byType(FloatingPill),
        matching: find.byType(Material),
      ),
    );

    expect(pill().center.dx, phone.width / 2);
    // Scrolling down hides the pill.
    await tester.drag(timeline.first, const Offset(0, -300));
    await tester.pumpAndSettle();
    final position = scrolled();
    expect(position, greaterThan(0));
    expect(navOffset(), isNot(Offset.zero));

    // Unfolded, it comes back in the corner, and the cards widen.
    resize(unfolded);
    await tester.pumpAndSettle();
    expect(navOffset(), Offset.zero);
    expect(pill().right, unfolded.width - 16);
    expect(scrolled(), position);
    final card = tester.getRect(find.byType(StatusCard).first);
    expect(card.width, feedWidth);
    expect(card.center.dx, unfolded.width / 2);
    expect(
      tester.widget<Icon>(find.byIcon(Icons.notifications_outlined)).color,
      unreadColor,
    );

    // Folded again, the pill is back in the middle, the list where it was.
    resize(phone);
    await tester.pumpAndSettle();
    expect(pill().center.dx, phone.width / 2);
    expect(scrolled(), position);

    // In the corner it still switches tabs, and its open tab scrolls back to
    // the top.
    resize(unfolded);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('搜索'));
    await tester.pumpAndSettle();
    expect(find.byType(StatusCard), findsNothing);
    await tester.tap(find.byTooltip('首页'));
    await tester.pumpAndSettle();
    expect(scrolled(), position);
    await tester.tap(find.byTooltip('首页'));
    await tester.pumpAndSettle();
    expect(scrolled(), 0);
  });
}

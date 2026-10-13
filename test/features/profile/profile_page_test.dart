import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/data/models/weibo_user.dart';
import 'package:maxbo/features/profile/profile_page.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;
  late ResponseBody Function(RequestOptions) handler;

  ResponseBody respond(RequestOptions o) => switch (o.uri.path) {
    '/ajax/profile/info' => jsonBody({
      'ok': 1,
      'data': profileDataJson(user: userJson(id: 1001, name: '示例用户')),
    }),
    '/ajax/statuses/mymblog' => jsonBody(
      userStatusesJson([
        statusJson(id: 1, text: '置顶的微博', extra: {'isTop': 1}),
        statusJson(id: 2, text: '普通的微博'),
      ], sinceId: ''),
    ),
    final path => throw StateError('unexpected request to $path'),
  };

  List<Map<String, String>> requestsTo(String path) => [
    for (final r in adapter.requests)
      if (r.uri.path == path) r.uri.queryParameters,
  ];

  Future<void> pumpPage(
    WidgetTester tester,
    ProfilePage page, {
    ResponseBody Function(RequestOptions)? answer,
  }) async {
    handler = answer ?? respond;
    adapter = FakeAdapter((o) => handler(o));
    await tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: signedInOverrides(adapter),
        child: MaterialApp(home: page),
      ),
    );
  }

  testWidgets('by uid: shows who was tapped, then the profile and statuses', (
    tester,
  ) async {
    await pumpPage(
      tester,
      const ProfilePage(
        uid: '1001',
        initial: WeiboUser(id: '1001', screenName: '示例用户'),
      ),
    );
    // App bar and header, before anything arrives.
    expect(find.text('示例用户'), findsNWidgets(2));

    await tester.pumpAndSettle();

    expect(find.text('这是一段简介'), findsOneWidget);
    expect(find.text('上海 浦东新区'), findsOneWidget);
    expect(find.text('已关注'), findsOneWidget);
    expect(find.textContaining('12.3万 粉丝'), findsOneWidget);
    expect(find.text('置顶的微博'), findsOneWidget);
    expect(find.text('置顶'), findsOneWidget);
    expect(find.text('普通的微博'), findsOneWidget);
    expect(find.text('没有更多了'), findsOneWidget);
    // Both load at once, each once.
    expect(requestsTo('/ajax/profile/info'), [
      {'uid': '1001'},
    ]);
    expect(requestsTo('/ajax/statuses/mymblog').single['uid'], '1001');
  });

  testWidgets('by name: looks the user up, then lists their statuses', (
    tester,
  ) async {
    await pumpPage(tester, const ProfilePage(screenName: '示例用户'));
    await tester.pumpAndSettle();

    expect(adapter.requests.map((r) => r.uri.path), [
      '/ajax/profile/info',
      '/ajax/statuses/mymblog',
    ]);
    expect(requestsTo('/ajax/profile/info').single, {'screen_name': '示例用户'});
    expect(requestsTo('/ajax/statuses/mymblog').single['uid'], '1001');
    expect(find.text('普通的微博'), findsOneWidget);
  });

  testWidgets('an unknown name says so and loads nothing more', (tester) async {
    await pumpPage(
      tester,
      const ProfilePage(screenName: '没有这个人'),
      answer: (_) => jsonBody({'ok': 1, 'user': <String, Object?>{}}),
    );
    await tester.pumpAndSettle();

    expect(find.text('找不到这个用户'), findsOneWidget);
    expect(find.text('重试'), findsOneWidget);
    expect(requestsTo('/ajax/statuses/mymblog'), isEmpty);
  });

  testWidgets('a failed profile keeps the statuses, with a retry', (
    tester,
  ) async {
    await pumpPage(
      tester,
      const ProfilePage(uid: '1001'),
      answer: (o) => o.uri.path == '/ajax/profile/info'
          ? jsonBody({'ok': 0, 'message': '服务繁忙'}, status: 500)
          : respond(o),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('服务繁忙'), findsOneWidget);
    expect(find.text('普通的微博'), findsOneWidget);

    handler = respond;
    await tester.tap(find.text('重试'));
    await tester.pumpAndSettle();

    expect(find.text('这是一段简介'), findsOneWidget);
    // The statuses were fine and stay as they are.
    expect(requestsTo('/ajax/statuses/mymblog'), hasLength(1));
  });
}

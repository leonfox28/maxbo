import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/features/hot_search/hot_search_page.dart';

import '../../support/fakes.dart';
import '../../support/routes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;
  late ResponseBody Function(RequestOptions) respond;

  Future<void> pumpPage(WidgetTester tester) async {
    adapter = FakeAdapter((o) => respond(o));
    await tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: signedInOverrides(adapter),
        child: MaterialApp.router(
          routerConfig: stubRouter(const HotSearchPage()),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  final list = hotSearchJson(
    hotgovs: [
      {'word': '#置顶的话题#', 'icon_desc': '热'},
    ],
    realtime: [
      hotItemJson('第一条', realpos: 1, icon: '热', num: 1587784),
      hotItemJson('付费的一条', icon: '首发', smallIcon: '商', isAd: 1),
      hotItemJson('第二条', realpos: 2, scheme: '第二条', num: 0),
    ],
  );

  testWidgets('lists the pinned entries, then the ranking as sent', (
    tester,
  ) async {
    respond = (_) => jsonBody(list);
    await pumpPage(tester);

    expect(adapter.requests.single.uri.path, '/ajax/side/hotSearch');
    final words = ['置顶的话题', '第一条', '付费的一条', '第二条'];
    final tops = [for (final w in words) tester.getTopLeft(find.text(w)).dy];
    expect(tops, orderedEquals([...tops]..sort()));
    expect(find.text('1'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
    expect(find.text('158万'), findsOneWidget);
    // Paid entries keep Weibo's 商 label.
    expect(find.text('商'), findsOneWidget);
    expect(find.text('首发'), findsNothing);
  });

  testWidgets('opens Weibo search for an entry in the app', (tester) async {
    respond = (_) => jsonBody(list);
    await pumpPage(tester);

    await tester.tap(find.text('第一条'));
    await tester.pumpAndSettle();
    final topic = openedWebUrl(tester);
    expect(topic.host, 'm.weibo.cn');
    expect(topic.path, '/search');
    expect(topic.queryParameters, {'containerid': '231522', 'q': '#第一条#'});

    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('第二条'));
    await tester.pumpAndSettle();
    expect(openedWebUrl(tester).queryParameters, {
      'containerid': '100103',
      'q': '第二条',
    });
  });

  testWidgets('a failed load can be retried', (tester) async {
    respond = (_) => jsonBody({'ok': 0, 'message': '服务繁忙'}, status: 500);
    await pumpPage(tester);
    expect(find.text('服务繁忙'), findsOneWidget);

    respond = (_) => jsonBody(list);
    await tester.tap(find.text('重试'));
    await tester.pumpAndSettle();
    expect(find.text('第一条'), findsOneWidget);
  });
}

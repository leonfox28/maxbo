import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/features/search/search_results_page.dart';
import 'package:maxbo/widgets/status_card/status_card.dart';

import '../../support/fakes.dart';
import '../../support/routes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;
  late ResponseBody Function(RequestOptions) respond;

  List<Map<String, String>> searches() => [
    for (final r in adapter.requests)
      if (r.uri.path == '/ajax/statuses/search') r.uri.queryParameters,
  ];

  Future<void> pumpPage(WidgetTester tester, String query) async {
    adapter = FakeAdapter((o) => respond(o));
    await tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: signedInOverrides(adapter),
        child: MaterialApp.router(
          routerConfig: stubRouter(SearchResultsPage(query: query)),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('a topic: its lead and counts head the posts in it', (
    tester,
  ) async {
    respond = (_) => jsonBody(
      searchJson([
        statusJson(id: 1, text: '第一条结果'),
        statusJson(id: 2, text: '第二条结果'),
      ], topicHeads: topicHeadsJson(name: '某个话题', summary: '话题导语')),
    );
    await pumpPage(tester, '#某个话题#');

    expect(searches().single['q'], '#某个话题#');
    // In the app bar and atop the list.
    expect(find.text('#某个话题#'), findsNWidgets(2));
    expect(find.text('4226万阅读 · 9365讨论 · 1997原创人数'), findsOneWidget);
    expect(find.textContaining('话题导语'), findsOneWidget);
    expect(find.text('第一条结果'), findsOneWidget);
    expect(find.text('第二条结果'), findsOneWidget);
  });

  testWidgets('the app bar hides scrolling down, returns scrolling up', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    respond = (_) => jsonBody(
      searchJson([
        statusJson(id: 1, text: '第一条结果'),
        statusJson(id: 2, text: '第二条结果'),
      ], topicHeads: topicHeadsJson(name: '某个话题', summary: '话题导语')),
    );
    await pumpPage(tester, '#某个话题#');
    final title = find
        .descendant(of: find.byType(AppBar), matching: find.text('#某个话题#'))
        .hitTestable();
    expect(title, findsOneWidget);

    await tester.drag(find.text('第一条结果'), const Offset(0, -150));
    await tester.pumpAndSettle();
    expect(title, findsNothing);

    await tester.drag(find.text('第二条结果'), const Offset(0, 60));
    await tester.pumpAndSettle();
    expect(title, findsOneWidget);
  });

  testWidgets('a word: just the posts, a page at a time', (tester) async {
    respond = (o) => jsonBody(switch (o.uri.queryParameters['page']) {
      '1' => searchJson([statusJson(id: 1, text: '第一页的结果')]),
      '2' => searchJson([statusJson(id: 2, text: '第二页的结果')]),
      _ => searchJson([]),
    });
    await pumpPage(tester, '某个词');

    expect(find.text('某个词'), findsOneWidget);
    expect(find.textContaining('导语'), findsNothing);
    expect(find.textContaining('阅读'), findsNothing);
    expect(find.text('第一页的结果'), findsOneWidget);

    // Too short to scroll: the footer offers more.
    await tester.tap(find.text('加载更多'));
    await tester.pumpAndSettle();
    expect(find.text('第二页的结果'), findsOneWidget);
    await tester.tap(find.text('加载更多'));
    await tester.pumpAndSettle();
    expect(find.text('没有更多了'), findsOneWidget);
    expect([for (final q in searches()) q['page']], ['1', '2', '3']);
  });

  testWidgets('nothing found says so', (tester) async {
    respond = (_) => jsonBody(searchJson([], total: 0));
    await pumpPage(tester, '没有结果的词');

    expect(find.text('没有找到相关微博'), findsOneWidget);
  });

  testWidgets('a failed search can be retried', (tester) async {
    respond = (_) => jsonBody({'ok': 0, 'message': '服务繁忙'}, status: 500);
    await pumpPage(tester, '某个词');
    expect(find.text('服务繁忙'), findsOneWidget);

    respond = (_) => jsonBody(searchJson([statusJson(text: '找到的微博')]));
    await tester.tap(find.text('重试'));
    await tester.pumpAndSettle();
    expect(find.text('找到的微博'), findsOneWidget);
  });

  testWidgets('the topic searched for does not open again; others do', (
    tester,
  ) async {
    respond = (_) => jsonBody(
      searchJson([
        statusJson(
          text:
              '<a href="//s.weibo.com/weibo?q=%23某个话题%23">#某个话题#</a>和'
              '<a href="//s.weibo.com/weibo?q=%23别的话题%23">#别的话题#</a>',
        ),
      ], topicHeads: topicHeadsJson(name: '某个话题')),
    );
    await pumpPage(tester, '#某个话题#');

    await tester.tapOnText(
      find.textRange.ofSubstring(
        '#某个话题#',
        descendentOf: find.byType(StatusCard),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('results '), findsNothing);

    await tester.tapOnText(find.textRange.ofSubstring('#别的话题#'));
    await tester.pumpAndSettle();
    expect(openedSearch(tester), '#别的话题#');
  });

  testWidgets("Weibo's own results are a tap away", (tester) async {
    respond = (_) => jsonBody(searchJson([statusJson()]));
    await pumpPage(tester, '#某个话题#');

    await tester.tap(find.byTooltip('网页版搜索结果'));
    await tester.pumpAndSettle();
    final url = openedWebUrl(tester);
    expect(url.host, 'm.weibo.cn');
    expect(url.queryParameters, {'containerid': '231522', 'q': '#某个话题#'});
  });
}

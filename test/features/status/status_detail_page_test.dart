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

  // Answers to writes, by path; a test sets what it needs.
  var writes = <String, ResponseBody Function()>{};
  var commentsCount = 12345;

  ResponseBody respond(RequestOptions o) => switch (o.uri.path) {
    '/ajax/statuses/show' => jsonBody({
      'ok': 1,
      ...statusJson(
        id: 1,
        text: '完整的正文',
        extra: {'attitudes_count': 5, 'comments_count': commentsCount},
      ),
    }),
    '/ajax/statuses/buildComments' => jsonBody(
      commentsPageJson([
        commentJson(
          text: o.uri.queryParameters['flow'] == '1' ? '最新的评论' : '最热的评论',
        ),
        commentJson(
          id: 6000000000000002,
          text: '我写的评论',
          user: userJson(id: 7, name: '我'),
        ),
      ]),
    ),
    final path when writes.containsKey(path) => writes[path]!(),
    '/ajax/statuses/repostTimeline' => jsonBody(
      repostsPageJson([statusJson(id: 8, text: '转发了这条')]),
    ),
    final path => throw StateError('unexpected request to $path'),
  };

  int requestsTo(String path) =>
      adapter.requests.where((r) => r.uri.path == path).length;

  Future<void> pumpPage(WidgetTester tester, {int comments = 12345}) async {
    writes = {};
    commentsCount = comments;
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

  testWidgets('the bars hide scrolling down, return scrolling up', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await pumpPage(tester);
    await tester.pumpAndSettle();
    final title = find.text('微博正文').hitTestable();
    Offset commentBar() =>
        tester.widget<AnimatedSlide>(find.byType(AnimatedSlide)).offset;
    expect(title, findsOneWidget);
    expect(commentBar(), Offset.zero);

    await tester.drag(find.text('完整的正文'), const Offset(0, -150));
    await tester.pumpAndSettle();
    expect(title, findsNothing);
    expect(commentBar(), isNot(Offset.zero));

    await tester.drag(find.text('最热的评论'), const Offset(0, 60));
    await tester.pumpAndSettle();
    expect(title, findsOneWidget);
    expect(commentBar(), Offset.zero);
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

  Map<String, String> sentForm() => Uri.splitQueryString(adapter.bodies.last);

  testWidgets('a comment is sent from the bar and goes atop the list', (
    tester,
  ) async {
    await pumpPage(tester);
    await tester.pumpAndSettle();
    writes['/ajax/comments/create'] = () => jsonBody({
      'ok': 1,
      'msg': '评论成功',
      'data': commentJson(id: 9, text: '新评论', user: userJson(id: 7)),
    });

    await tester.tap(find.text('写评论…'));
    await tester.pumpAndSettle();
    expect(
      tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
      isNull,
    );
    await tester.enterText(find.byType(TextField), '  新评论 ');
    await tester.pump();
    await tester.tap(find.text('发送'));
    await tester.pumpAndSettle();

    expect(adapter.requests.last.uri.path, '/ajax/comments/create');
    expect(sentForm()['id'], '1');
    expect(sentForm()['comment'], '新评论');
    expect(find.byType(TextField), findsNothing);
    expect(find.text('评论成功'), findsOneWidget);
    expect(find.text('评论 1.2万'), findsOneWidget);
    final texts = tester
        .widgetList<Text>(find.byType(Text))
        .map((t) => t.data ?? t.textSpan?.toPlainText())
        .toList();
    expect(texts.indexOf('新评论'), lessThan(texts.indexOf('最热的评论')));
  });

  testWidgets('a failed comment stays in the sheet with the reason', (
    tester,
  ) async {
    await pumpPage(tester);
    await tester.pumpAndSettle();
    writes['/ajax/comments/create'] = () =>
        jsonBody({'ok': 0, 'msg': '评论太频繁了'});

    await tester.tap(find.text('写评论…'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '新评论');
    await tester.pump();
    await tester.tap(find.text('发送'));
    await tester.pumpAndSettle();

    expect(find.text('评论太频繁了'), findsOneWidget);
    expect(find.text('新评论'), findsOneWidget);

    // Closing the sheet keeps the draft for next time.
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNothing);
    await tester.tap(find.text('写评论…'));
    await tester.pumpAndSettle();
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      '新评论',
    );
  });

  testWidgets('tapping a comment replies to it', (tester) async {
    await pumpPage(tester);
    await tester.pumpAndSettle();
    writes['/ajax/comments/reply'] = () =>
        jsonBody({'ok': 1, 'data': commentJson(id: 10, text: '回复内容')});

    await tester.tap(find.text('最热的评论'));
    await tester.pumpAndSettle();
    expect(find.text('回复 @评论者'), findsOneWidget);
    await tester.enterText(find.byType(TextField), '回复内容');
    await tester.pump();
    await tester.tap(find.text('发送'));
    await tester.pumpAndSettle();

    expect(adapter.requests.last.uri.path, '/ajax/comments/reply');
    expect(sentForm()['id'], '1');
    expect(sentForm()['cid'], '6000000000000001');
    // The comment's thread preview now has the reply.
    expect(find.text('共1条回复 >'), findsOneWidget);
  });

  testWidgets('the user can delete their own comments only', (tester) async {
    await pumpPage(tester);
    await tester.pumpAndSettle();
    writes['/ajax/statuses/destroyComment'] = () =>
        jsonBody({'ok': 1, 'data': commentJson()});

    // Others' comments only offer a reply.
    await tester.longPress(find.text('最热的评论'));
    await tester.pumpAndSettle();
    expect(find.text('删除'), findsNothing);
    expect(find.text('回复 @评论者'), findsOneWidget);
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('我写的评论'));
    await tester.pumpAndSettle();
    await tester.longPress(find.text('我写的评论'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();
    expect(find.text('确定删除这条评论吗？'), findsOneWidget);
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();

    expect(adapter.requests.last.uri.path, '/ajax/statuses/destroyComment');
    expect(sentForm(), {'cid': '6000000000000002'});
    expect(find.text('我写的评论'), findsNothing);
    expect(find.text('已删除'), findsOneWidget);
  });

  testWidgets('the bar likes the status, and the tab count follows', (
    tester,
  ) async {
    await pumpPage(tester);
    await tester.pumpAndSettle();
    writes['/ajax/statuses/setLike'] = () => jsonBody({'ok': 1});
    expect(find.text('赞 5'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.thumb_up_outlined).last);
    await tester.pumpAndSettle();

    expect(adapter.requests.last.uri.path, '/ajax/statuses/setLike');
    expect(sentForm(), {'id': '1'});
    expect(find.text('赞 6'), findsOneWidget);
  });

  testWidgets('a comment is counted until data that has it loads', (
    tester,
  ) async {
    await pumpPage(tester, comments: 3);
    await tester.pumpAndSettle();
    writes['/ajax/comments/create'] = () =>
        jsonBody({'ok': 1, 'data': commentJson(id: 9, text: '新评论')});

    await tester.tap(find.text('写评论…'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '新评论');
    await tester.pump();
    await tester.tap(find.text('发送'));
    await tester.pumpAndSettle();
    expect(find.text('评论 4'), findsOneWidget);

    // Weibo now counts it too.
    commentsCount = 4;
    await tester.fling(find.text('完整的正文'), const Offset(0, 400), 1000);
    await tester.pumpAndSettle();

    expect(requestsTo('/ajax/statuses/show'), 2);
    expect(find.text('评论 4'), findsOneWidget);
  });
}

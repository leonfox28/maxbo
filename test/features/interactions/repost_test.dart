import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/data/models/status.dart';
import 'package:maxbo/features/interactions/repost.dart';
import 'package:maxbo/features/timeline/timeline_controller.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;
  late ResponseBody Function() answer;

  // A repost of post 1, by user 5, whose text lists cut short.
  final longRepost = statusJson(
    id: 2,
    text: '截断的转发理由',
    user: userJson(id: 5, name: '中间人'),
    retweeted: statusJson(id: 1),
    extra: {'isLongText': true, 'text_raw': '截断的'},
  );

  ResponseBody respond(RequestOptions o) => switch (o.uri.path) {
    '/ajax/statuses/normal_repost' => answer(),
    '/ajax/statuses/show' => jsonBody({
      'ok': 1,
      ...longRepost,
      'longText': {'content': '完整的转发理由'},
    }),
    '/ajax/feed/friendstimeline' => jsonBody(feedJson([statusJson(id: 1)])),
    final path => throw StateError('unexpected request to $path'),
  };

  Map<String, String> sentForm() => Uri.splitQueryString(adapter.bodies.last);

  TextEditingController field(WidgetTester tester) =>
      tester.widget<TextField>(find.byType(TextField)).controller!;

  Finder sendButton() => find.widgetWithText(FilledButton, '转发');

  /// A page with a button that reposts [json]; returns its container.
  Future<ProviderContainer> pumpButton(
    WidgetTester tester,
    Map<String, Object?> json, {
    VoidCallback? onReposted,
  }) async {
    answer = () => jsonBody({
      'ok': 1,
      'data': {
        'statuses': statusJson(
          id: 78,
          text: '转发微博',
          user: userJson(id: 7, name: '我'),
          retweeted: statusJson(id: 1),
        ),
      },
    });
    adapter = FakeAdapter(respond);
    final status = Status.tryFromJson(json)!;
    await tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: signedInOverrides(adapter),
        child: MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () =>
                    repostStatus(context, status, onReposted: onReposted),
                child: const Text('打开'),
              ),
            ),
          ),
        ),
      ),
    );
    return ProviderScope.containerOf(tester.element(find.text('打开')));
  }

  Future<void> open(WidgetTester tester) async {
    await tester.tap(find.text('打开'));
    await tester.pumpAndSettle();
  }

  testWidgets('reposts a post for the chosen audience, and the repost goes '
      'atop the open timeline', (tester) async {
    var reposted = 0;
    final container = await pumpButton(
      tester,
      statusJson(id: 1),
      onReposted: () => reposted++,
    );
    // Open, as the home tab keeps it.
    container.listen(timelineProvider, (_, _) {});
    await tester.pumpAndSettle();

    await open(tester);
    // An original starts empty, and goes as it is.
    expect(field(tester).text, isEmpty);
    expect(tester.widget<FilledButton>(sendButton()).onPressed, isNotNull);
    await tester.tap(find.text('同时评论'));
    await tester.tap(find.text('公开'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('仅自己可见'));
    await tester.pumpAndSettle();
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(adapter.requests.last.uri.path, '/ajax/statuses/normal_repost');
    expect(sentForm(), {
      'id': '1',
      'is_comment': '1',
      'comment_ori': '0',
      'is_repost': '0',
      'visible': '1',
    });
    expect(find.byType(TextField), findsNothing);
    expect(find.text('转发成功'), findsOneWidget);
    expect(reposted, 1);
    expect(
      container.read(timelineProvider).requireValue.statuses.map((s) => s.id),
      ['78', '1'],
    );
  });

  testWidgets('a repost of a repost starts with the chain it carries on, '
      'the cursor before it', (tester) async {
    await pumpButton(
      tester,
      statusJson(
        id: 2,
        text: '<a href="/n/朋友">@朋友</a> 转发理由',
        user: userJson(id: 5, name: '中间人'),
        retweeted: statusJson(id: 1),
        extra: {'text_raw': '@朋友 转发理由'},
      ),
    );

    await open(tester);
    expect(field(tester).text, '//@中间人:@朋友 转发理由');
    expect(field(tester).selection, const TextSelection.collapsed(offset: 0));
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(sentForm()['id'], '2');
    expect(sentForm()['comment'], '//@中间人:@朋友 转发理由');
  });

  testWidgets('a long repost cut short in a list loads in full first', (
    tester,
  ) async {
    await pumpButton(tester, longRepost);

    await open(tester);

    expect(adapter.requests.single.uri.path, '/ajax/statuses/show');
    expect(field(tester).text, '//@中间人:完整的转发理由');
  });

  testWidgets('a failed repost stays in the sheet with the reason, and the '
      'draft outlives it', (tester) async {
    await pumpButton(tester, statusJson(id: 1));
    answer = () => jsonBody({'ok': 0, 'msg': '转发太频繁了'});

    await open(tester);
    await tester.enterText(find.byType(TextField), '我的看法');
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(find.text('转发太频繁了'), findsOneWidget);
    expect(find.text('转发成功'), findsNothing);

    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNothing);
    await open(tester);
    expect(field(tester).text, '我的看法');
  });
}

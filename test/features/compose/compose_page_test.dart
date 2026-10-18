import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/features/compose/compose_draft.dart';
import 'package:maxbo/features/compose/compose_page.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;
  var failPost = false;
  var picked = <String>[];
  int? pickLimit;

  ResponseBody respond(RequestOptions o) => switch (o.uri.path) {
    '/ajax/statuses/update' when failPost => jsonBody({
      'ok': 0,
      'msg': '发布太频繁',
    }),
    '/ajax/statuses/update' => jsonBody({
      'ok': 1,
      'data': statusJson(id: 9, user: userJson(id: 7)),
    }),
    final path => throw StateError('unexpected request to $path'),
  };

  Future<ProviderContainer> pumpPage(WidgetTester tester) async {
    failPost = false;
    pickLimit = null;
    adapter = FakeAdapter(respond);
    await tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: [
          ...signedInOverrides(adapter),
          picturePickerProvider.overrideWithValue((limit) async {
            pickLimit = limit;
            return picked;
          }),
        ],
        child: MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(builder: (_) => const ComposePage()),
                ),
                child: const Text('写'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('写'));
    await tester.pumpAndSettle();
    return ProviderScope.containerOf(tester.element(find.byType(ComposePage)));
  }

  Finder sendButton() => find.widgetWithText(FilledButton, '发送');

  testWidgets('sends what the user wrote, for whom they chose', (tester) async {
    await pumpPage(tester);
    expect(tester.widget<FilledButton>(sendButton()).onPressed, isNull);

    await tester.enterText(find.byType(TextField), '第一条微博');
    await tester.tap(find.text('公开'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('仅自己可见'));
    await tester.pumpAndSettle();
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(Uri.splitQueryString(adapter.bodies.single), {
      'content': '第一条微博',
      'visible': '1',
    });
    expect(find.byType(ComposePage), findsNothing);
    expect(find.text('已发送'), findsOneWidget);
  });

  testWidgets('a failed post stays with the reason, and the draft outlives '
      'the page', (tester) async {
    await pumpPage(tester);
    failPost = true;
    await tester.enterText(find.byType(TextField), '草稿');
    await tester.pump();
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(find.byType(ComposePage), findsOneWidget);
    expect(find.text('发布太频繁'), findsOneWidget);

    await tester.tap(find.byTooltip('关闭'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('写'));
    await tester.pumpAndSettle();

    expect(find.text('草稿'), findsOneWidget);
  });

  testWidgets('picks pictures while there is room, and removes them', (
    tester,
  ) async {
    final c = await pumpPage(tester);
    picked = ['/nowhere/a.jpg', '/nowhere/b.jpg'];

    await tester.tap(find.byIcon(Icons.add_photo_alternate_outlined));
    await tester.pumpAndSettle();

    expect(pickLimit, maxDraftPictures);
    expect(find.byTooltip('移除'), findsNWidgets(2));

    await tester.tap(find.byTooltip('移除').first);
    await tester.pumpAndSettle();

    expect(c.read(composeDraftProvider).pictures.map((p) => p.path), [
      '/nowhere/b.jpg',
    ]);

    picked = [for (var i = 0; i < 8; i++) '/nowhere/$i.jpg'];
    await tester.tap(find.byIcon(Icons.add_photo_alternate_outlined));
    await tester.pumpAndSettle();

    expect(pickLimit, maxDraftPictures - 1);
    expect(find.byTooltip('移除'), findsNWidgets(9));
    expect(find.byIcon(Icons.add_photo_alternate_outlined), findsNothing);
  });
}

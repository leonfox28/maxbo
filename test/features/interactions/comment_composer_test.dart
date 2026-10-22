import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_exception.dart';
import 'package:maxbo/features/compose/picture_picker.dart';
import 'package:maxbo/features/interactions/comment_composer.dart';

import '../../support/fakes.dart';

void main() {
  late FakeAdapter adapter;
  var allowed = true;
  var uploads = 0;
  var picked = <String>[];
  int? pickLimit;
  late Map<String, CommentDraft> drafts;

  ResponseBody respond(RequestOptions o) => switch (o.uri.path) {
    '/ajax/statuses/checkAllowCommentWithPic' => jsonBody({
      'ok': 1,
      'result': allowed,
    }),
    '/interface/pic_upload.php' => htmlBody(
      '{"code":"A00006","data":{"pics":{"pic_1":{"pid":"pid${++uploads}"}}}}',
    ),
    final path => throw StateError('unexpected request to $path'),
  };

  List<String> paths() => [for (final r in adapter.requests) r.uri.path];

  Future<void> pumpSheet(
    WidgetTester tester, {
    required Future<void> Function(String text, String? pid) send,
  }) async {
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
                onPressed: () => showCommentComposer(
                  context,
                  statusId: '42',
                  hint: '写评论…',
                  send: send,
                  drafts: drafts,
                  draftKey: 'status',
                ),
                child: const Text('评论'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('评论'));
    await tester.pumpAndSettle();
  }

  setUp(() {
    allowed = true;
    uploads = 0;
    picked = [];
    pickLimit = null;
    drafts = {};
  });

  Finder sendButton() => find.widgetWithText(FilledButton, '发送');

  Future<void> addPicture(WidgetTester tester) async {
    await tester.tap(find.byTooltip('添加图片'));
    await tester.pumpAndSettle();
  }

  testWidgets('a picture alone can go, uploaded once when it is sent', (
    tester,
  ) async {
    final dir = Directory.systemTemp.createTempSync('maxbo_comment');
    addTearDown(() => dir.deleteSync(recursive: true));
    picked = [(File('${dir.path}/a.gif')..writeAsStringSync('GIF89a')).path];
    final sent = <(String, String?)>[];
    var fail = true;
    await pumpSheet(
      tester,
      send: (text, pid) async {
        if (fail) throw const WeiboApiException('评论太频繁了');
        sent.add((text, pid));
      },
    );
    expect(tester.widget<FilledButton>(sendButton()).onPressed, isNull);

    await addPicture(tester);

    // Weibo is asked first whether comments here take pictures.
    expect(paths(), ['/ajax/statuses/checkAllowCommentWithPic']);
    expect(adapter.requests.single.uri.queryParameters, {'id': '42'});
    expect(pickLimit, 1);
    expect(find.byTooltip('移除'), findsOneWidget);
    expect(find.byTooltip('添加图片'), findsNothing);
    expect(tester.widget<FilledButton>(sendButton()).onPressed, isNotNull);

    // The file is read for real, outside the test's fake time.
    await tester.runAsync(() async {
      await tester.tap(sendButton());
      await Future<void>.delayed(const Duration(milliseconds: 200));
    });
    await tester.pumpAndSettle();

    expect(find.text('评论太频繁了'), findsOneWidget);
    expect(adapter.bodies.last, 'GIF89a');
    expect(uploads, 1);

    fail = false;
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(uploads, 1);
    expect(sent, [('', 'pid1')]);
    expect(find.byType(TextField), findsNothing);
    expect(drafts, isEmpty);
  });

  testWidgets('says so when comments on the status take no pictures', (
    tester,
  ) async {
    allowed = false;
    await pumpSheet(tester, send: (_, _) async {});

    await addPicture(tester);

    expect(find.text('这条微博的评论不能带图'), findsOneWidget);
    expect(pickLimit, isNull);
    expect(
      tester
          .widget<IconButton>(
            find.ancestor(
              of: find.byIcon(Icons.add_photo_alternate_outlined),
              matching: find.byType(IconButton),
            ),
          )
          .onPressed,
      isNull,
    );
  });

  testWidgets('the picture stays with the draft, and can be taken out', (
    tester,
  ) async {
    picked = ['/nowhere/a.jpg'];
    final sent = <(String, String?)>[];
    await pumpSheet(tester, send: (text, pid) async => sent.add((text, pid)));
    await tester.enterText(find.byType(TextField), '看图');
    await addPicture(tester);

    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    await tester.tap(find.text('评论'));
    await tester.pumpAndSettle();

    expect(find.text('看图'), findsOneWidget);
    expect(find.byTooltip('移除'), findsOneWidget);

    await tester.tap(find.byTooltip('移除'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('添加图片'), findsOneWidget);

    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(uploads, 0);
    expect(sent, [('看图', null)]);
  });
}

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/data/models/comment.dart';
import 'package:maxbo/features/comments/comment_replies_page.dart';
import 'package:maxbo/features/comments/comments_controller.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;

  late ProviderContainer container;
  const CommentsQuery statusComments = (
    statusId: '42',
    authorId: '1001',
    order: CommentOrder.hot,
  );

  ResponseBody respond(RequestOptions o) => switch (o.uri.path) {
    // The status page's list, below the thread.
    '/ajax/statuses/buildComments'
        when o.uri.queryParameters['fetch_level'] == '0' =>
      jsonBody(commentsPageJson([commentJson(id: 20, replyCount: 2)])),
    '/ajax/statuses/buildComments' => jsonBody(
      commentsPageJson([
        commentJson(id: 21, text: '别人的回复'),
        commentJson(
          id: 22,
          text: '我的回复',
          user: userJson(id: 7, name: '我'),
        ),
      ]),
    ),
    '/ajax/comments/reply' => jsonBody({
      'ok': 1,
      'data': commentJson(id: 23, text: '新的回复', user: userJson(id: 7)),
    }),
    '/ajax/statuses/destroyComment' => jsonBody({'ok': 1}),
    final path => throw StateError('unexpected request to $path'),
  };

  Future<void> pumpPage(WidgetTester tester) async {
    adapter = FakeAdapter(respond);
    container = ProviderContainer(
      retry: noRetry,
      overrides: signedInOverrides(adapter),
    );
    addTearDown(container.dispose);
    container.listen(commentsProvider(statusComments), (_, _) {});
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          home: CommentRepliesPage(
            thread: CommentThread(
              root: Comment.tryFromJson(
                commentJson(id: 20, text: '根评论', replyCount: 2),
              )!,
              statusId: '42',
              authorId: '1001',
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Comment rootOnStatusPage() => container
      .read(commentsProvider(statusComments))
      .requireValue
      .items
      .single;

  Map<String, String> sentForm() => Uri.splitQueryString(adapter.bodies.last);

  Future<void> send(WidgetTester tester, String text) async {
    await tester.enterText(find.byType(TextField), text);
    await tester.pump();
    await tester.tap(find.text('发送'));
    await tester.pumpAndSettle();
  }

  testWidgets('the bar replies to the thread, newest first', (tester) async {
    await pumpPage(tester);
    expect(find.text('2 条回复'), findsOneWidget);

    await tester.tap(find.text('回复 @评论者'));
    await tester.pumpAndSettle();
    await send(tester, '新的回复');

    expect(adapter.requests.last.uri.path, '/ajax/comments/reply');
    expect(sentForm()['id'], '42');
    expect(sentForm()['cid'], '20');
    expect(find.text('3 条回复'), findsOneWidget);
    expect(find.text('回复成功'), findsOneWidget);
    expect(rootOnStatusPage().replyCount, 3);
    expect(rootOnStatusPage().replies.single.text, '新的回复');
    expect(
      tester.getTopLeft(find.text('新的回复')).dy,
      lessThan(tester.getTopLeft(find.text('别人的回复')).dy),
    );
  });

  testWidgets('tapping a reply replies to it', (tester) async {
    await pumpPage(tester);

    await tester.tap(find.text('别人的回复'));
    await tester.pumpAndSettle();
    await send(tester, '新的回复');

    expect(sentForm()['cid'], '21');
  });

  testWidgets('the user can delete their own replies', (tester) async {
    await pumpPage(tester);

    await tester.longPress(find.text('我的回复'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();

    expect(sentForm(), {'cid': '22'});
    expect(find.text('我的回复'), findsNothing);
    expect(find.text('1 条回复'), findsOneWidget);
    expect(rootOnStatusPage().replyCount, 1);
  });
}

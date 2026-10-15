import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/data/models/notice.dart';
import 'package:maxbo/features/notifications/notices_controller.dart';
import 'package:maxbo/features/notifications/notifications_page.dart';
import 'package:maxbo/widgets/tab_reselect.dart';

import '../../support/fakes.dart';
import '../../support/routes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;
  late ProviderContainer container;
  late Map<String, Object?> unreadCounts;
  final taps = ChangeNotifier();
  tearDownAll(taps.dispose);

  ResponseBody respond(RequestOptions o) => switch (o.uri.path) {
    '/2/remind/push_count.json' => jsonBody(unreadCounts),
    '/ajax/message/cmt' => jsonBody(
      noticePageJson('comments', [
        commentNoticeJson(
          id: 11,
          text: '收到的评论',
          status: statusJson(id: 101, text: '我发的那条'),
          replyTo: commentJson(id: 10, text: '我之前的评论'),
        ),
      ]),
    ),
    '/ajax/comments/mentions' => jsonBody(
      noticePageJson('comments', [commentNoticeJson(id: 12, text: '提到我的评论')]),
    ),
    '/ajax/statuses/mentions' => jsonBody(
      noticePageJson('statuses', [statusJson(id: 102, text: '提到我的微博')]),
    ),
    '/ajax/message/attitudes' => jsonBody(
      noticePageJson('attitudes', [
        likeNoticeJson(
          id: 13,
          status: statusJson(id: 103, text: '被赞的微博'),
          cut: true,
        ),
      ]),
    ),
    final path => throw StateError('unexpected request to $path'),
  };

  setUp(() => unreadCounts = {});

  Future<void> pumpPage(WidgetTester tester) async {
    adapter = FakeAdapter(respond);
    container = ProviderContainer(
      retry: noRetry,
      overrides: signedInOverrides(adapter),
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(
          routerConfig: stubRouter(
            TabReselect(taps: taps, child: const NotificationsPage()),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  List<String> paths() => [for (final r in adapter.requests) r.uri.path];

  Future<void> checkUnread(WidgetTester tester) async {
    // Real async: the fake clock wouldn't let the request finish.
    await tester.runAsync(
      () => container.read(unreadNoticesProvider.notifier).check(),
    );
    await tester.pumpAndSettle();
  }

  Finder richText(String text) => find.textContaining(text, findRichText: true);

  Finder tab(String label) =>
      find.descendant(of: find.byType(TabBar), matching: find.text(label));

  testWidgets('loads a tab only when it is opened', (tester) async {
    await pumpPage(tester);
    expect(paths(), ['/ajax/message/cmt']);
    expect(find.text('收到的评论'), findsOneWidget);
    expect(richText('我之前的评论'), findsOneWidget);
    expect(richText('我发的那条'), findsOneWidget);

    await tester.tap(tab('@我的微博'));
    await tester.pumpAndSettle();
    expect(paths().last, '/ajax/statuses/mentions');
    expect(find.text('提到我的微博'), findsOneWidget);

    await tester.tap(tab('赞'));
    await tester.pumpAndSettle();
    expect(paths().last, '/ajax/message/attitudes');
    expect(richText('点赞的人 赞了你的微博'), findsOneWidget);

    // Lists stay loaded when the user comes back to them.
    await tester.tap(tab('评论'));
    await tester.pumpAndSettle();
    expect(paths().where((p) => p == '/ajax/message/cmt'), hasLength(1));
  });

  testWidgets('a dot marks unread tabs until they are opened', (tester) async {
    await pumpPage(tester);
    unreadCounts = {'cmt': 1, 'attitude': 2};
    await checkUnread(tester);

    // The comments tab is open: it reloads, and is read.
    expect(paths().where((p) => p == '/ajax/message/cmt'), hasLength(2));
    expect(container.read(unreadNoticesProvider).kinds, {NoticeKind.likes});
    expect(find.bySemanticsLabel(RegExp('评论，有未读')), findsNothing);
    expect(find.bySemanticsLabel(RegExp('赞，有未读')), findsOneWidget);

    await tester.tap(tab('赞'));
    await tester.pumpAndSettle();
    expect(paths().last, '/ajax/message/attitudes');
    expect(container.read(unreadNoticesProvider).any, isFalse);
    expect(find.bySemanticsLabel(RegExp('赞，有未读')), findsNothing);
  });

  testWidgets('a loaded list keeps its dot until its tab is opened', (
    tester,
  ) async {
    await pumpPage(tester);
    await tester.tap(tab('赞'));
    await tester.pumpAndSettle();
    await tester.tap(tab('评论'));
    await tester.pumpAndSettle();
    int likeLoads() =>
        paths().where((p) => p == '/ajax/message/attitudes').length;
    expect(likeLoads(), 1);

    unreadCounts = {'attitude': 1};
    await checkUnread(tester);
    expect(likeLoads(), 1);
    expect(find.bySemanticsLabel(RegExp('赞，有未读')), findsOneWidget);

    // Read elsewhere, say: the dot still waits for the user.
    unreadCounts = {};
    await checkUnread(tester);
    expect(find.bySemanticsLabel(RegExp('赞，有未读')), findsOneWidget);

    await tester.tap(tab('赞'));
    await tester.pumpAndSettle();
    expect(likeLoads(), 2);
    expect(container.read(unreadNoticesProvider).any, isFalse);
  });

  testWidgets('notices open the post they are about', (tester) async {
    await pumpPage(tester);
    await tester.tap(find.text('收到的评论'));
    await tester.pumpAndSettle();
    expect(find.text('status 101'), findsOneWidget);

    Navigator.of(tester.element(find.text('status 101'))).pop();
    await tester.pumpAndSettle();
    await tester.tap(tab('赞'));
    await tester.pumpAndSettle();
    // A cut-down post opens by id alone.
    await tester.tap(richText('被赞的微博'));
    await tester.pumpAndSettle();
    expect(find.text('status 103'), findsOneWidget);
  });
}

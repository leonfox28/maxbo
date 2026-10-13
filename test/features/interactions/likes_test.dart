import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_exception.dart';
import 'package:maxbo/data/models/comment.dart';
import 'package:maxbo/data/models/status.dart';
import 'package:maxbo/features/comments/comment_tile.dart';
import 'package:maxbo/features/interactions/likes.dart';
import 'package:maxbo/widgets/status_card/status_card.dart';

import '../../support/fakes.dart';
import '../../support/routes.dart';
import '../../support/statuses.dart';

const LikeTarget _status = (kind: LikeKind.status, id: '42');
const _unliked = LikeState(liked: false, count: 5);

void main() {
  late FakeAdapter adapter;
  late FutureOr<ResponseBody> Function(RequestOptions) respond;

  setUp(() => adapter = FakeAdapter((o) => respond(o)));

  group('LikesController', () {
    late ProviderContainer c;

    setUp(() {
      c = ProviderContainer(
        retry: noRetry,
        overrides: signedInOverrides(adapter),
      );
      addTearDown(c.dispose);
    });

    LikeState shown(LikeState loaded) =>
        LikesController.resolve(c.read(likesProvider)[_status], loaded);

    test('shows a like at once, and keeps it once Weibo agrees', () async {
      final answer = Completer<ResponseBody>();
      respond = (_) => answer.future;

      final toggling = c.read(likesProvider.notifier).toggle(_status, _unliked);
      expect(shown(_unliked), const LikeState(liked: true, count: 6));
      expect(c.read(likesProvider)[_status]?.pending, isTrue);

      answer.complete(jsonBody({'ok': 1, 'attitude': 'heart'}));
      await toggling;
      expect(shown(_unliked), const LikeState(liked: true, count: 6));
      expect(c.read(likesProvider)[_status]?.pending, isFalse);
      expect(adapter.requests.single.uri.path, '/ajax/statuses/setLike');
    });

    test('takes the like back when Weibo refuses it', () async {
      respond = (_) => jsonBody({'ok': 1, 'attitude': 'heart'});
      await c.read(likesProvider.notifier).toggle(_status, _unliked);

      respond = (_) => jsonBody({'ok': 1, 'result': false});
      await expectLater(
        c.read(likesProvider.notifier).toggle(_status, _unliked),
        throwsA(isA<WeiboApiException>()),
      );

      expect(adapter.requests.last.uri.path, '/ajax/statuses/cancelLike');
      expect(shown(_unliked), const LikeState(liked: true, count: 6));
    });

    test('ignores toggles while one is on its way', () async {
      final answer = Completer<ResponseBody>();
      respond = (_) => answer.future;

      final first = c.read(likesProvider.notifier).toggle(_status, _unliked);
      await c.read(likesProvider.notifier).toggle(_status, _unliked);
      answer.complete(jsonBody({'ok': 1}));
      await first;

      expect(adapter.requests, hasLength(1));
      expect(shown(_unliked).liked, isTrue);
    });

    test('newer data wins over a change made on older data', () async {
      respond = (_) => jsonBody({'ok': 1});
      await c.read(likesProvider.notifier).toggle(_status, _unliked);

      // Reloaded: liked, and by others meanwhile.
      const reloaded = LikeState(liked: true, count: 9);
      expect(shown(reloaded), reloaded);
      // A list still holding the old data shows the change.
      expect(shown(_unliked), const LikeState(liked: true, count: 6));

      respond = (_) => jsonBody({'ok': 1, 'result': true});
      await c.read(likesProvider.notifier).toggle(_status, reloaded);
      expect(shown(reloaded), const LikeState(liked: false, count: 8));
      // The old data says unliked, which is right again.
      expect(shown(_unliked), _unliked);
    });

    test('comments use the comment endpoints', () async {
      respond = (_) => jsonBody({'ok': 1, 'result': true});

      await c.read(likesProvider.notifier).toggle((
        kind: LikeKind.comment,
        id: '9',
      ), _unliked);

      expect(adapter.requests.single.uri.path, '/ajax/statuses/updateLike');
    });
  });

  group('on a status card', () {
    Future<void> pumpCards(WidgetTester tester) => tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: signedInOverrides(adapter),
        child: MaterialApp.router(
          routerConfig: stubRouter(
            Scaffold(
              body: ListView(
                children: [
                  // The same status, as two lists would show it.
                  for (var i = 0; i < 2; i++)
                    StatusCard(
                      Status.tryFromJson(
                        statusJson(id: 42, extra: {'attitudes_count': 5}),
                      )!,
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    testWidgets('liking shows on every card of the status', (tester) async {
      respond = (_) => jsonBody({'ok': 1, 'attitude': 'heart'});
      await pumpCards(tester);
      expect(find.text('5'), findsNWidgets(2));

      await tester.tap(find.text('5').first);
      await tester.pumpAndSettle();

      expect(find.text('6'), findsNWidgets(2));
      expect(find.byIcon(Icons.thumb_up), findsNWidgets(2));
      expect(adapter.requests.single.uri.path, '/ajax/statuses/setLike');
    });

    testWidgets('a refused like is taken back and explained', (tester) async {
      respond = (_) => jsonBody({'ok': 0, 'msg': '操作过于频繁'});
      await pumpCards(tester);

      await tester.tap(find.text('5').first);
      await tester.pumpAndSettle();

      expect(find.text('5'), findsNWidgets(2));
      expect(find.byIcon(Icons.thumb_up), findsNothing);
      expect(find.text('操作过于频繁'), findsOneWidget);
    });

    testWidgets('a security check is offered, not taken', (tester) async {
      respond = (_) => jsonBody({
        'ok': -100,
        'url': 'https://security.weibo.com/captcha/geetest?x=1',
      });
      await pumpCards(tester);

      await tester.tap(find.text('5').first);
      await tester.pumpAndSettle();

      expect(find.text('需要先完成微博的安全验证'), findsOneWidget);
      await tester.tap(find.text('去验证'));
      await tester.pumpAndSettle();

      expect(openedWebUrl(tester).path, '/captcha/geetest');
      // The user likes again themselves once through.
      expect(adapter.requests, hasLength(1));
    });
  });

  testWidgets('a comment\'s like button likes it, not replies', (tester) async {
    respond = (_) => jsonBody({'ok': 1, 'result': true});
    var tapped = 0;
    await tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: signedInOverrides(adapter),
        child: MaterialApp(
          home: Scaffold(
            body: CommentTile(
              Comment.tryFromJson(commentJson(id: 9, likes: 3))!,
              onTap: () => tapped++,
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('3'));
    await tester.pumpAndSettle();

    expect(find.text('4'), findsOneWidget);
    expect(adapter.requests.single.uri.path, '/ajax/statuses/updateLike');
    expect(Uri.splitQueryString(adapter.bodies.single)['object_id'], '9');
    expect(tapped, 0);
  });
}

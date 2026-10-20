import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/data/models/status.dart';
import 'package:maxbo/features/status/status_actions.dart';
import 'package:maxbo/widgets/status_card/status_card.dart';

import '../../support/routes.dart';
import '../../support/statuses.dart';

void main() {
  Future<void> pumpCard(WidgetTester tester) => tester.pumpWidget(
    ProviderScope(
      retry: noRetry,
      child: MaterialApp.router(
        routerConfig: stubRouter(
          Scaffold(
            body: Builder(
              builder: (context) => StatusCard(
                Status.tryFromJson(
                  statusJson(
                    user: userJson(id: 1001, name: '作者'),
                    text:
                        '看<a href=/n/朋友 usercard="name=朋友">@朋友</a>的'
                        '<a href="//s.weibo.com/weibo?q=%23话题%23">#话题#</a>',
                  ),
                )!,
                actions: statusCardActions(context),
              ),
            ),
          ),
        ),
      ),
    ),
  );

  Future<void> back(WidgetTester tester) async {
    tester.state<NavigatorState>(find.byType(Navigator).last).pop();
    await tester.pumpAndSettle();
  }

  testWidgets('the avatar and name open the author', (tester) async {
    await pumpCard(tester);

    await tester.tap(find.byType(ClipOval));
    await tester.pumpAndSettle();
    expect(find.text('user 1001'), findsOneWidget);

    await back(tester);
    await tester.tap(find.text('作者'));
    await tester.pumpAndSettle();
    expect(find.text('user 1001'), findsOneWidget);
  });

  testWidgets('a mention opens the user by name', (tester) async {
    await pumpCard(tester);

    await tester.tapOnText(find.textRange.ofSubstring('@朋友'));
    await tester.pumpAndSettle();
    expect(find.text('name 朋友'), findsOneWidget);
  });

  testWidgets('a topic opens the posts in it', (tester) async {
    await pumpCard(tester);

    await tester.tapOnText(find.textRange.ofSubstring('#话题#'));
    await tester.pumpAndSettle();
    expect(openedSearch(tester), '#话题#');
  });
}

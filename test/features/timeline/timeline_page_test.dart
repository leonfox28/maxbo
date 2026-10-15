import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/features/notifications/notices_controller.dart';
import 'package:maxbo/features/timeline/timeline_page.dart';

import '../../support/fakes.dart';
import '../../support/routes.dart';
import '../../support/statuses.dart';

void main() {
  testWidgets('pulling to refresh checks notifications, once a minute', (
    tester,
  ) async {
    final adapter = FakeAdapter(
      (RequestOptions o) => o.uri.path == '/2/remind/push_count.json'
          ? jsonBody({'cmt': 1})
          : jsonBody(feedJson([statusJson(text: '首页的一条微博')])),
    );
    final container = ProviderContainer(
      retry: noRetry,
      overrides: signedInOverrides(adapter),
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(
          routerConfig: stubRouter(const TimelinePage()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    int checks() => adapter.requests
        .where((o) => o.uri.path == '/2/remind/push_count.json')
        .length;
    int feeds() => adapter.requests
        .where((o) => o.uri.path == '/ajax/feed/friendstimeline')
        .length;
    // Opening the timeline alone doesn't check.
    expect(checks(), 0);

    await tester.fling(find.text('首页的一条微博'), const Offset(0, 400), 1000);
    await tester.pumpAndSettle();
    expect(feeds(), 2);
    expect(checks(), 1);
    expect(container.read(unreadNoticesProvider).any, isTrue);

    await tester.fling(find.text('首页的一条微博'), const Offset(0, 400), 1000);
    await tester.pumpAndSettle();
    expect(feeds(), 3);
    expect(checks(), 1);
  });
}

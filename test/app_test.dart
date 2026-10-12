import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/app.dart';

void main() {
  testWidgets('starts on the timeline tab and switches tabs', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: MaxboApp()));

    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.text('关注时间线将在 M2 实现'), findsOneWidget);

    await tester.tap(find.text('热搜'));
    await tester.pumpAndSettle();

    expect(find.text('热搜榜将在 M4 实现'), findsOneWidget);
  });
}

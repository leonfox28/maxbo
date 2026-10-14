import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/widgets/tab_reselect.dart';

void main() {
  late ChangeNotifier taps;
  late int refreshes;
  late Completer<void> refreshing;

  setUp(() {
    taps = ChangeNotifier();
    refreshes = 0;
  });
  tearDown(() => taps.dispose());

  Future<void> pumpList(WidgetTester tester) => tester.pumpWidget(
    MaterialApp(
      home: TabReselect(
        taps: taps,
        child: Scaffold(
          body: TabRefreshIndicator(
            onRefresh: () {
              refreshes++;
              refreshing = Completer();
              return refreshing.future;
            },
            child: ListView.builder(
              primary: true,
              itemCount: 50,
              itemBuilder: (_, i) => SizedBox(height: 100, child: Text('$i')),
            ),
          ),
        ),
      ),
    ),
  );

  double scrolled(WidgetTester tester) =>
      tester.state<ScrollableState>(find.byType(Scrollable)).position.pixels;

  testWidgets('a tap scrolls back to the top, the next one refreshes', (
    tester,
  ) async {
    await pumpList(tester);
    await tester.drag(find.byType(ListView), const Offset(0, -1500));
    await tester.pumpAndSettle();
    expect(scrolled(tester), greaterThan(0));

    taps.notifyListeners();
    await tester.pumpAndSettle();
    expect(scrolled(tester), 0);
    expect(refreshes, 0);

    taps.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(refreshes, 1);
    expect(find.byType(RefreshProgressIndicator), findsOneWidget);

    // Tapping again while it runs doesn't start another.
    taps.notifyListeners();
    await tester.pump();
    expect(refreshes, 1);

    refreshing.complete();
    await tester.pumpAndSettle();
    expect(find.byType(RefreshProgressIndicator), findsNothing);
  });
}

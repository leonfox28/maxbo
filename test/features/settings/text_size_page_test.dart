import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/settings/app_settings.dart';
import 'package:maxbo/features/settings/text_size_page.dart';

import '../../support/fakes.dart';

void main() {
  late InMemorySettingsStorage storage;

  Future<void> pumpPage(
    WidgetTester tester, {
    TextSize size = TextSize.standard,
  }) async {
    storage = InMemorySettingsStorage();
    await tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: [
          initialSettingsProvider.overrideWithValue(
            AppSettings(textSize: size),
          ),
          settingsStorageProvider.overrideWithValue(storage),
        ],
        child: const MaterialApp(home: TextSizePage()),
      ),
    );
    await tester.pumpAndSettle();
  }

  double sliderValue(WidgetTester tester) =>
      tester.widget<Slider>(find.byType(Slider)).value;

  testWidgets('shows samples over the current size', (tester) async {
    await pumpPage(tester, size: TextSize.large);

    expect(find.textContaining('拖动下面的滑块'), findsOneWidget);
    expect(find.text('「标准」就是系统设置里的字体大小。'), findsOneWidget);
    expect(find.text('大'), findsOneWidget);
    expect(sliderValue(tester), TextSize.large.index);
    // Read out by name, not as a number.
    final slider = tester.widget<Slider>(find.byType(Slider));
    expect(slider.semanticFormatterCallback!(slider.value), '大');
  });

  testWidgets('two steps either way of standard, saved as chosen', (
    tester,
  ) async {
    await pumpPage(tester);
    expect(sliderValue(tester), 2);

    await tester.drag(find.byType(Slider), const Offset(1000, 0));
    await tester.pumpAndSettle();
    expect(storage.stored.textSize, TextSize.extraLarge);
    expect(find.text('特大'), findsOneWidget);
    expect(
      tester.widget<IconButton>(find.byType(IconButton).last).onPressed,
      isNull,
    );

    await tester.drag(find.byType(Slider), const Offset(-1000, 0));
    await tester.pumpAndSettle();
    expect(storage.stored.textSize, TextSize.extraSmall);
    expect(find.text('特小'), findsOneWidget);
    expect(
      tester.widget<IconButton>(find.byType(IconButton).first).onPressed,
      isNull,
    );
  });

  testWidgets('the A buttons step down and up', (tester) async {
    await pumpPage(tester, size: TextSize.extraSmall);
    Color? colorOf(Finder a) => tester
        .widget<Text>(find.descendant(of: a, matching: find.text('A')))
        .style
        ?.color;
    final down = find.byTooltip('调小');
    final up = find.byTooltip('调大');
    // Dimmed where there's no further step.
    expect(colorOf(down), isNot(colorOf(up)));

    await tester.tap(up);
    await tester.pumpAndSettle();
    expect(storage.stored.textSize, TextSize.small);
    expect(colorOf(down), colorOf(up));
    for (var i = 0; i < 2; i++) {
      await tester.tap(up);
      await tester.pumpAndSettle();
    }
    expect(storage.stored.textSize, TextSize.large);
    for (var i = 0; i < 2; i++) {
      await tester.tap(down);
      await tester.pumpAndSettle();
    }
    expect(storage.stored.textSize, TextSize.small);
    expect(storage.saves, 5);
  });

  testWidgets('the samples do nothing when tapped', (tester) async {
    await pumpPage(tester);

    // A real like button would call Weibo, which isn't faked here.
    await tester.tap(find.text('46'), warnIfMissed: false);
    await tester.tap(find.text('maxbo'), warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(find.text('46'), findsOneWidget);
    expect(find.byType(TextSizePage), findsOneWidget);
  });
}

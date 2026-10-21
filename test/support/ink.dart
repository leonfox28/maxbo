import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// The colours of the ink that shows on the Material [finder]'s widget is
/// drawn on, such as a press highlight; see-through ink is left out.
Iterable<Color> visibleInk(WidgetTester tester, Finder finder) {
  final material = Material.of(tester.element(finder));
  final features =
      (material as dynamic).debugInkFeatures as List<InkFeature>? ?? const [];
  return [
    for (final feature in features)
      if (feature is InteractiveInkFeature && feature.color.a > 0)
        feature.color,
  ];
}

/// Holds a press on [finder] past the press timeout and the highlight's
/// fade-in, and returns the ink that shows meanwhile.
Future<Iterable<Color>> inkWhilePressing(
  WidgetTester tester,
  Finder finder,
) async {
  final press = await tester.startGesture(tester.getCenter(finder));
  await tester.pump(const Duration(milliseconds: 150));
  await tester.pump(const Duration(milliseconds: 300));
  final ink = visibleInk(tester, finder);
  await press.cancel();
  await tester.pumpAndSettle();
  return ink;
}

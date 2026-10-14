import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/app_info.dart';

void main() {
  test('appVersion matches pubspec.yaml', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    final version = RegExp(
      r'^version: *([^+\s]+)',
      multiLine: true,
    ).firstMatch(pubspec)?.group(1);
    expect(appVersion, version);
  });
}

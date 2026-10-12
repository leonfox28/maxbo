import 'package:flutter/material.dart';

// Deliberately not Weibo's brand orange: maxbo should not look official.
const _seedColor = Color(0xFF3F51B5);

ThemeData buildTheme(Brightness brightness) {
  return ThemeData(
    colorScheme: ColorScheme.fromSeed(
      seedColor: _seedColor,
      brightness: brightness,
    ),
  );
}

import 'package:flutter/material.dart';

import 'page_transitions.dart';

// Deliberately not Weibo's brand orange: maxbo should not look official.
const _seedColor = Color(0xFF3F51B5);

ThemeData buildTheme(Brightness brightness) {
  return ThemeData(
    colorScheme: ColorScheme.fromSeed(
      seedColor: _seedColor,
      brightness: brightness,
    ),
    pageTransitionsTheme: pageTransitionsTheme,
  );
}

/// Marks what has something new for the user, such as unread notifications.
/// The same red in light and dark themes, unlike the scheme's error colour.
const unreadColor = Color(0xFFF5222D);

import 'dart:convert';

/// Extracts the `window.$CONFIG = {...}` object that weibo.com embeds in its
/// pages, or returns null if the page has none or it isn't valid JSON.
///
/// The web app reads the signed-in user from it; there is no JSON endpoint
/// for that (`/ajax/config` answers 404).
Map<String, Object?>? extractPageConfig(String html) {
  final marker = html.indexOf(r'window.$CONFIG');
  if (marker < 0) return null;
  final start = html.indexOf('{', marker);
  if (start < 0) return null;
  final end = _matchingBrace(html, start);
  if (end < 0) return null;
  try {
    final config = jsonDecode(html.substring(start, end + 1));
    return config is Map<String, Object?> ? config : null;
  } on FormatException {
    return null;
  }
}

/// Index of the `}` closing the object that opens at [start], skipping
/// braces inside JSON strings; -1 if it never closes.
int _matchingBrace(String s, int start) {
  var depth = 0;
  var inString = false;
  for (var i = start; i < s.length; i++) {
    final c = s[i];
    if (inString) {
      if (c == r'\') {
        i++;
      } else if (c == '"') {
        inString = false;
      }
    } else if (c == '"') {
      inString = true;
    } else if (c == '{') {
      depth++;
    } else if (c == '}' && --depth == 0) {
      return i;
    }
  }
  return -1;
}

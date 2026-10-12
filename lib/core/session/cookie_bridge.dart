import 'package:flutter/services.dart';

/// Reads cookies from the platform WebView cookie store.
///
/// webview_flutter can only write cookies, and Weibo's session cookie (SUB)
/// is HttpOnly, so it has to be read natively. The native halves live in
/// `android/.../CookieBridge.kt` and `ios/Runner/AppDelegate.swift`.
class CookieBridge {
  const CookieBridge();

  static const _channel = MethodChannel('app.maxbo/cookies');

  /// Returns the cookies the WebView would send to [url], keyed by name.
  ///
  /// When the store holds several cookies with the same name (for example
  /// one per domain), the last one wins.
  Future<Map<String, String>> getCookies(Uri url) async {
    final cookies = await _channel.invokeListMethod<Map<Object?, Object?>>(
      'getCookies',
      {'url': url.toString()},
    );
    return {
      for (final cookie in cookies ?? const <Map<Object?, Object?>>[])
        cookie['name']! as String: cookie['value']! as String,
    };
  }
}

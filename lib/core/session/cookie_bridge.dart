import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// Reads cookies from the platform WebView cookie store.
///
/// webview_flutter can only write cookies, and Weibo's session cookie (SUB)
/// is HttpOnly, so it has to be read natively. The native half lives in
/// `android/.../CookieBridge.kt`.
class CookieBridge {
  const CookieBridge();

  static const _channel = MethodChannel('io.github.leonfox28.maxbo/cookies');

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

  /// The WebView's cookies for Weibo's two sites, keyed by site.
  Future<Map<String, Map<String, String>>> readWeiboCookies() async => {
    'weibo.com': await getCookies(Uri.parse('https://weibo.com/')),
    'weibo.cn': await getCookies(Uri.parse('https://m.weibo.cn/')),
  };

  /// Deletes every WebView cookie, signing the embedded browser out.
  Future<void> clearAll() async {
    await WebViewCookieManager().clearCookies();
  }
}

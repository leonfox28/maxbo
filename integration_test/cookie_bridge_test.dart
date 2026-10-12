import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:maxbo/core/session/cookie_bridge.dart';
import 'package:webview_flutter/webview_flutter.dart';

// Runs on a device or simulator: checks that the native CookieBridge reads
// the same cookie store that webview_flutter writes to.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('reads cookies written to the WebView store', (tester) async {
    final manager = WebViewCookieManager();
    await manager.clearCookies();
    // Keep the value URL-safe: webview_flutter percent-encodes values when
    // writing on Android but not on iOS.
    await manager.setCookie(
      const WebViewCookie(
        name: 'maxbo_probe',
        value: 'probe_1',
        domain: 'example.com',
      ),
    );

    final cookies = await const CookieBridge().getCookies(
      Uri.parse('https://example.com/'),
    );

    expect(cookies['maxbo_probe'], 'probe_1');
    await manager.clearCookies();
  });
}

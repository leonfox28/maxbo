import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:maxbo/core/session/cookie_bridge.dart';
import 'package:webview_flutter/webview_flutter.dart';

// Runs on a device or simulator: checks that the native CookieBridge reads
// the cookie store the WebView uses, including HttpOnly cookies set by a page
// (Weibo's SUB is HttpOnly and invisible to page scripts).
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  final manager = WebViewCookieManager();
  setUp(manager.clearCookies);
  tearDown(manager.clearCookies);

  testWidgets('reads cookies written to the WebView store', (tester) async {
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
  });

  testWidgets('reads HttpOnly cookies set by a page response', (tester) async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);
    server.listen((request) {
      request.response
        ..headers.add('Set-Cookie', 'maxbo_http_only=probe_2; HttpOnly; Path=/')
        ..headers.contentType = ContentType.html
        ..write('<html><body>ok</body></html>');
      unawaited(request.response.close());
    });
    final url = Uri.parse('http://localhost:${server.port}/');

    final loaded = Completer<void>();
    final controller = WebViewController();
    await controller.setNavigationDelegate(
      NavigationDelegate(
        onPageFinished: (_) {
          if (!loaded.isCompleted) loaded.complete();
        },
      ),
    );
    await tester.pumpWidget(
      MaterialApp(home: WebViewWidget(controller: controller)),
    );
    await controller.loadRequest(url);
    await loaded.future.timeout(const Duration(seconds: 20));

    final cookies = await const CookieBridge().getCookies(url);

    expect(cookies['maxbo_http_only'], 'probe_2');
  });
}

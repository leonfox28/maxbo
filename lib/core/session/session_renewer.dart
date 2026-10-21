import 'dart:async';

import 'package:webview_flutter/webview_flutter.dart';

import '../net/weibo_dio.dart';
import '../util/dev_log.dart';
import 'cookie_bridge.dart';

/// Renews a lapsed session the way a browser does.
///
/// Weibo honours `SUB` for about a week, but signing in also leaves a 30-day
/// auto-login cookie (`ALC`) on passport.weibo.com. A browser that opens
/// weibo.com with a lapsed `SUB` ends up on passport's visitor page (at times
/// after a detour through login.sina.com.cn), whose script trades `ALC` for a
/// new `SUB` and another 30 days (PLAN §8). Renewing lets Weibo's own pages
/// do just that in a WebView, which shares its cookies with the login page.
abstract interface class SessionRenewer {
  /// Opens weibo.com and offers the WebView's cookies to [adopt] each time a
  /// page leaves a `SUB` other than [lapsedSub] and those offered before.
  ///
  /// Returns true once [adopt] accepts them, or false when it turns down one
  /// Weibo set meanwhile (a visitor's, when there was nothing to renew), or
  /// when Weibo takes too long.
  Future<bool> renew({
    required String? lapsedSub,
    required Future<bool> Function(Map<String, Map<String, String>> cookies)
    adopt,
  });
}

/// Follows the pages of one renewal; see [SessionRenewer.renew].
class RenewalTracker {
  RenewalTracker({
    required String? lapsedSub,
    required this.startSub,
    required this.adopt,
  }) : _offered = {lapsedSub};

  /// The WebView's `SUB` before the first page.
  final String? startSub;
  final Future<bool> Function(Map<String, Map<String, String>> cookies) adopt;
  final Set<String?> _offered;

  /// Handles the [cookies] a main-frame page left behind: true once [adopt]
  /// accepted them, false if Weibo won't renew, or null to wait for the next
  /// page.
  Future<bool?> pageLoaded(Map<String, Map<String, String>> cookies) async {
    final sub = cookies['weibo.com']?['SUB'];
    if (sub == null || !_offered.add(sub)) return null;
    if (await adopt(cookies)) return true;
    // The WebView's own session may have lapsed too, and is renewed in turn;
    // one Weibo set on the way is all it is going to set.
    return sub == startSub ? null : false;
  }
}

const _blank = 'about:blank';

/// Renews in a WebView that is never shown.
class WebViewSessionRenewer implements SessionRenewer {
  const WebViewSessionRenewer(
    this._bridge, {
    this.timeout = const Duration(seconds: 20),
  });

  final CookieBridge _bridge;
  final Duration timeout;

  @override
  Future<bool> renew({
    required String? lapsedSub,
    required Future<bool> Function(Map<String, Map<String, String>> cookies)
    adopt,
  }) async {
    final tracker = RenewalTracker(
      lapsedSub: lapsedSub,
      startSub: (await _bridge.readWeiboCookies())['weibo.com']?['SUB'],
      adopt: adopt,
    );
    final result = Completer<bool>();
    // Pages are handled one at a time: adopting one takes a request.
    var handled = Future<void>.value();
    void loaded(String? url, [String? error]) {
      handled = handled.then((_) async {
        if (result.isCompleted) return;
        final page = url == null ? null : Uri.tryParse(url);
        try {
          final cookies = await _bridge.readWeiboCookies();
          // No query: passport's carries tickets.
          devLog(
            'session renewal: ${page?.host}${page?.path} ${error ?? 'loaded'}, '
            'weibo.com=${cookies['weibo.com']?.keys.toList()}',
          );
          final outcome = await tracker.pageLoaded(cookies);
          if (outcome != null && !result.isCompleted) result.complete(outcome);
        } on Exception catch (e) {
          devLog('session renewal: $e');
        }
      });
    }

    final controller = WebViewController();
    await controller.setJavaScriptMode(JavaScriptMode.unrestricted);
    await controller.setUserAgent(kBrowserUserAgent);
    await controller.setNavigationDelegate(
      NavigationDelegate(
        // Only web pages; once done, only the blank page that ends them.
        onNavigationRequest: (request) {
          final url = request.url;
          final to = Uri.tryParse(url);
          devLog('session renewal: to ${to?.host}${to?.path}');
          final allowed = result.isCompleted
              ? url == _blank
              : url.startsWith('https://') || url.startsWith('http://');
          return allowed
              ? NavigationDecision.navigate
              : NavigationDecision.prevent;
        },
        onPageFinished: loaded,
        // passport's last hop, through login.sina.com.cn, fails in a WebView
        // after the weibo.com cookies are set (PLAN §8 item 1).
        onWebResourceError: (error) {
          if (error.isForMainFrame ?? true) {
            loaded(error.url, 'failed (${error.errorCode})');
          }
        },
      ),
    );
    await controller.loadRequest(Uri.https('weibo.com', '/'));
    final renewed = await result.future.timeout(
      timeout,
      onTimeout: () => false,
    );
    // Leave nothing running: weibo.com's own pages poll for news.
    await controller.loadRequest(Uri.parse(_blank));
    return renewed;
  }
}

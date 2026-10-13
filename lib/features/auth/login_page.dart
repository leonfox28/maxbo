import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../core/net/weibo_dio.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/dev_log.dart';

/// Weibo's own sign-in page; after signing in it returns to weibo.com.
final kLoginUrl = Uri.https('passport.weibo.com', '/sso/signin', {
  'entry': 'miniblog',
  'source': 'miniblog',
  'url': 'https://weibo.com/',
});

/// Hosts Weibo's official login page in a WebView. The password is typed
/// into Weibo's page and never passes through maxbo.
///
/// After every main-frame load the WebView's cookies are checked against the
/// API, wherever the page landed: passport sets the weibo.com cookies before
/// its cross-domain hop through login.sina.com.cn, and that hop can fail
/// (HTTP 403 inside a WebView) without affecting the weibo.com session.
///
/// Pops with `true` once signed in.
class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  late final WebViewController _controller;
  int _progress = 0;
  bool _verifying = false;

  /// A SUB value the API identified as a visitor; not re-checked until the
  /// WebView receives a different one.
  String? _visitorSub;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(kBrowserUserAgent)
      ..setNavigationDelegate(
        NavigationDelegate(
          // Only web pages: block app links such as sinaweibo://.
          onNavigationRequest: (request) =>
              request.url.startsWith('https://') ||
                  request.url.startsWith('http://')
              ? NavigationDecision.navigate
              : NavigationDecision.prevent,
          onProgress: (progress) {
            if (mounted) setState(() => _progress = progress);
          },
          onPageFinished: (_) => _checkLogin(),
          onWebResourceError: (error) {
            if (error.isForMainFrame ?? true) _checkLogin();
          },
        ),
      )
      ..loadRequest(kLoginUrl);
  }

  Future<void> _checkLogin() async {
    if (_verifying || !mounted) return;

    final cookies = await ref.read(cookieBridgeProvider).readWeiboCookies();
    final sub = cookies['weibo.com']?['SUB'];
    devLog(
      'login check: weibo.com=${cookies['weibo.com']?.keys.toList()} '
      'weibo.cn=${cookies['weibo.cn']?.keys.toList()}',
    );
    if (sub == null || sub == _visitorSub || _verifying || !mounted) return;

    setState(() => _verifying = true);
    try {
      await ref.read(sessionProvider.notifier).completeLogin(cookies);
      if (mounted) context.pop(true);
    } on SessionExpiredException {
      // Visitor cookies only: the user hasn't signed in yet.
      devLog('login check: not signed in yet');
      _visitorSub = sub;
    } on WeiboException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => _verifying = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('登录微博'),
        actions: [
          IconButton(
            tooltip: '刷新',
            icon: const Icon(Icons.refresh),
            onPressed: _controller.reload,
          ),
        ],
      ),
      body: Column(
        children: [
          Container(
            width: double.infinity,
            color: theme.colorScheme.surfaceContainerHighest,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Text(
              '下方是微博官方登录页面。maxbo 不会接触你的密码，登录状态只保存在本机。',
              style: theme.textTheme.bodySmall,
            ),
          ),
          if (_progress < 100) LinearProgressIndicator(value: _progress / 100),
          Expanded(
            child: Stack(
              children: [
                WebViewWidget(controller: _controller),
                if (_verifying)
                  const ColoredBox(
                    color: Colors.black26,
                    child: Center(child: CircularProgressIndicator()),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

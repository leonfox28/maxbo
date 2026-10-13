import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../core/net/weibo_dio.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';

/// Weibo's own sign-in page; after signing in it returns to weibo.com.
final kLoginUrl = Uri.https('passport.weibo.com', '/sso/signin', {
  'entry': 'miniblog',
  'source': 'miniblog',
  'url': 'https://weibo.com/',
});

/// Hosts Weibo's official login page in a WebView. The password is typed
/// into Weibo's page and never passes through maxbo; once the WebView leaves
/// the passport pages, its cookies are checked against the API.
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
          onPageFinished: _checkLogin,
        ),
      )
      ..loadRequest(kLoginUrl);
  }

  Future<void> _checkLogin(String url) async {
    final host = Uri.tryParse(url)?.host ?? '';
    if (_verifying || !mounted || isLoginHost(host)) return;

    final cookies = await ref.read(cookieBridgeProvider).readWeiboCookies();
    if (cookies['weibo.com']?['SUB'] == null || !mounted) return;

    setState(() => _verifying = true);
    try {
      await ref.read(sessionProvider.notifier).completeLogin(cookies);
      if (mounted) context.pop(true);
    } on SessionExpiredException {
      // Visitor cookies only: the user hasn't signed in yet.
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

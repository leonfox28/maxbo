import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../core/net/weibo_dio.dart';

/// Weibo's search results for [query], e.g. `#话题#`, on its mobile site:
/// s.weibo.com is laid out for desktops and sends phones there.
Uri weiboSearchUrl(String query) {
  final topic =
      query.length > 2 && query.startsWith('#') && query.endsWith('#');
  return Uri.https('m.weibo.cn', '/search', {
    // What s.weibo.com redirects phones to: topic or general results.
    'containerid': topic ? '231522' : '100103',
    'q': query,
  });
}

/// Opens [url] in the in-app browser; completes when the user leaves it.
Future<void> openWebPage(BuildContext context, Uri url) => context.push(
  Uri(path: '/web', queryParameters: {'url': '$url'}).toString(),
);

/// A web page inside the app, for what maxbo has no page of its own for,
/// such as search results or Weibo's security checks.
///
/// It shares the WebView cookie store with the login page and sends the same
/// browser identity, so Weibo sees the signed-in user in the same browser.
class WebPage extends StatefulWidget {
  const WebPage({super.key, required this.url});

  final Uri url;

  @override
  State<WebPage> createState() => _WebPageState();
}

class _WebPageState extends State<WebPage> {
  late final WebViewController _controller;
  int _progress = 0;
  String? _title;
  bool _canGoBack = false;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(kBrowserUserAgent)
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: _navigate,
          onProgress: (progress) {
            if (mounted) setState(() => _progress = progress);
          },
          onUrlChange: (_) => _updateHistory(),
          onPageFinished: (_) => _updateHistory(),
        ),
      )
      ..loadRequest(widget.url);
  }

  NavigationDecision _navigate(NavigationRequest request) {
    final url = request.url;
    if (url.startsWith('https://')) return NavigationDecision.navigate;
    // Weibo still links and redirects to some pages over plain http, which
    // both platforms refuse; the same pages are served over https.
    if (url.startsWith('http://')) {
      if (!request.isMainFrame) return NavigationDecision.navigate;
      _controller.loadRequest(Uri.parse('https://${url.substring(7)}'));
    }
    // Anything else is an app link such as sinaweibo://.
    return NavigationDecision.prevent;
  }

  Future<void> _updateHistory() async {
    final title = await _controller.getTitle();
    final canGoBack = await _controller.canGoBack();
    if (!mounted) return;
    setState(() {
      _title = title;
      _canGoBack = canGoBack;
    });
  }

  Future<void> _openExternally() async {
    final current = await _controller.currentUrl();
    final url = current == null ? widget.url : Uri.parse(current);
    final opened = await launchUrl(url, mode: LaunchMode.externalApplication);
    if (!opened && mounted) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('无法打开链接')));
    }
  }

  @override
  Widget build(BuildContext context) {
    // Back steps back through the pages visited here before leaving.
    return PopScope(
      canPop: !_canGoBack,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _controller.goBack();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            _title ?? '',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          actions: [
            if (_canGoBack)
              IconButton(
                tooltip: '关闭',
                icon: const Icon(Icons.close),
                onPressed: () => Navigator.of(context).pop(),
              ),
            PopupMenuButton<VoidCallback>(
              onSelected: (action) => action(),
              itemBuilder: (_) => [
                PopupMenuItem(
                  value: _controller.reload,
                  child: const Text('刷新'),
                ),
                PopupMenuItem(
                  value: _openExternally,
                  child: const Text('在浏览器中打开'),
                ),
              ],
            ),
          ],
          bottom: _progress < 100
              ? PreferredSize(
                  preferredSize: const Size.fromHeight(2),
                  child: LinearProgressIndicator(
                    value: _progress / 100,
                    minHeight: 2,
                  ),
                )
              : null,
        ),
        body: WebViewWidget(controller: _controller),
      ),
    );
  }
}

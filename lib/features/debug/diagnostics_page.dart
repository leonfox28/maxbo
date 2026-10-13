import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/cookie_jar.dart';
import '../../core/net/request_throttle.dart';
import '../../core/net/weibo_dio.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/json_shape.dart';
import '../../data/api/weibo_api.dart';

const _mobileUserAgent =
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) '
    'AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 '
    'Safari/604.1';

// Fields that might mark a feed item as an ad.
const _adFlags = ['isAd', 'is_ad', 'ad_state', 'promotion', 'ad_marked'];
const _adLabels = ['readtimetype', 'mblogtypename', 'mark'];

/// Debug-only checks against the live API for the open questions in
/// docs/PLAN.md section 8.
///
/// Runs on the signed-in session, or on the login WebView's cookies when
/// signed out (to debug a login that didn't complete). The report holds field
/// names, counts and cookie names, never cookie values or post content;
/// numbers of 7+ digits and non-ASCII text in page snippets are masked. Each
/// line is also printed with a `[maxbo-diag]` prefix for reading over adb.
class DiagnosticsPage extends ConsumerStatefulWidget {
  const DiagnosticsPage({super.key});

  @override
  ConsumerState<DiagnosticsPage> createState() => _DiagnosticsPageState();
}

class _DiagnosticsPageState extends ConsumerState<DiagnosticsPage> {
  final _lines = <String>[];
  bool _running = false;

  void _log(String line) {
    debugPrint('[maxbo-diag] $line');
    if (mounted) setState(() => _lines.add(line));
  }

  Future<void> _run() async {
    setState(() {
      _lines.clear();
      _running = true;
    });

    final session = ref.read(sessionProvider);
    final bridge = ref.read(cookieBridgeProvider);
    final throttle = ref.read(requestThrottleProvider);
    final uid = session is SignedIn ? session.account.uid : null;
    final jar = session is SignedIn
        ? ref.read(cookieJarProvider)
        : WeiboCookieJar(await bridge.readWeiboCookies());
    _log('session: ${session is SignedIn ? 'signed in' : 'webview cookies'}');

    // Separate clients without the expiry callback: an odd answer to an
    // experimental request must not sign the user out.
    final dio = createWeiboDio(jar: jar, throttle: throttle);
    final api = WeiboApi(dio);
    final mobileApi = WeiboApi(
      createWeiboDio(jar: jar, throttle: throttle, userAgent: _mobileUserAgent),
    );

    Future<Map<String, Object?>?> fetch(
      String label,
      String url, [
      Map<String, Object?>? query,
      WeiboApi? client,
    ]) async {
      try {
        return await (client ?? api).getJson(url, query: query);
      } on Exception catch (e) {
        _log('$label: $e');
        return null;
      }
    }

    _log('== 1/6 cookies');
    for (final site in WeiboCookieJar.sites) {
      _log('jar $site: ${jar.names(site).join(', ')}');
    }
    final web = await bridge.readWeiboCookies();
    web.forEach(
      (site, cookies) => _log('webview $site: ${cookies.keys.join(', ')}'),
    );

    _log('== 4 who am I');
    final profile = await fetch(
      'profile/info',
      'https://weibo.com/ajax/profile/info',
    );
    if (profile != null) _log('profile/info: ${describeShape(profile)}');
    final mobileConfig = await fetch(
      'm config',
      'https://m.weibo.cn/api/config',
    );
    if (mobileConfig != null) {
      _log('m config: ${describeShape(mobileConfig)}');
    }
    await _probePage(dio, 'https://weibo.com/');

    _log('== 3 groups / list_id');
    final groups = await fetch(
      'allGroups',
      'https://weibo.com/ajax/feed/allGroups',
      {'is_new_segment': 1, 'fetch_hot': 1},
    );
    String maskId(Object? id) =>
        _redact(uid == null ? '$id' : '$id'.replaceAll(uid, '<uid>'));
    final systemGids = <String>[];
    if (groups != null) {
      _log(describeShape(groups, maxDepth: 5));
      for (final category in _maps(groups['groups'])) {
        for (final group in _maps(category['group'])) {
          // sysgroup 0 marks the user's own groups; others are Weibo's.
          final builtIn = group['sysgroup'] != 0;
          final channel = '${group['type']}' == '8888'; // Topic feeds.
          if (builtIn && !channel) {
            systemGids.add('${group['gid']}');
          }
          if (channel) continue;
          _log(
            'group type=${group['type']} sys=${group['sysgroup']} '
            'gid=${maskId(group['gid'])} '
            'title=${builtIn ? group['title'] : '<custom>'}',
          );
        }
      }
    }

    final variants = <String, String?>{
      'no list_id': null,
      if (uid != null) 'list_id=11000<uid>': '11000$uid',
      for (final gid in systemGids.take(6)) 'list_id=${maskId(gid)}': gid,
    };
    var statuses = <Map<String, Object?>>[];
    List<Object?>? baseline;
    for (final MapEntry(key: label, value: listId) in variants.entries) {
      final feed = await fetch(
        label,
        'https://weibo.com/ajax/feed/friendstimeline',
        {'refresh': 4, 'count': 20, 'list_id': ?listId},
      );
      if (feed == null) continue;
      final items = (feed['statuses'] as List? ?? const [])
          .whereType<Map<String, Object?>>()
          .toList();
      final ids = [for (final s in items) s['idstr']];
      baseline ??= ids;
      final shared = ids.where(baseline.contains).length;
      _log(
        '$label: ok=${feed['ok']} statuses=${items.length} '
        'same_as_first=$shared/${baseline.length} '
        'max_id=${feed['max_id'] != null} since_id=${feed['since_id'] != null}',
      );
      if (items.length > statuses.length) statuses = items;
    }

    _log('== 2 UA binding');
    final mobileFeed = await fetch(
      'mobile UA feed',
      'https://weibo.com/ajax/feed/friendstimeline',
      {'refresh': 4, 'count': 1},
      mobileApi,
    );
    if (mobileFeed != null) _log('mobile UA feed: ok=${mobileFeed['ok']}');

    _log('== 5 ad markers (${statuses.length} statuses)');
    for (final flag in _adFlags) {
      final count = statuses.where((s) => _truthy(s[flag])).length;
      _log('$flag: $count');
    }
    for (final label in _adLabels) {
      final values = {
        for (final s in statuses)
          if (s[label] case final String v when v.isNotEmpty)
            _redact(v.length > 40 ? '${v.substring(0, 40)}…' : v),
      };
      _log('$label: ${values.join(' | ')}');
    }
    final keys = {for (final s in statuses) ...s.keys}.toList()..sort();
    _log('status keys: ${keys.join(', ')}');

    _log('== done');
    if (mounted) setState(() => _running = false);
  }

  /// Logs where in an HTML page the current user's id might be embedded.
  Future<void> _probePage(Dio dio, String url) async {
    try {
      final response = await dio.get<String>(
        url,
        options: Options(
          responseType: ResponseType.plain,
          headers: {'Accept': 'text/html,application/xhtml+xml'},
        ),
      );
      final html = response.data ?? '';
      _log(
        'page $url -> ${response.statusCode} '
        'final=${response.realUri.host}${response.realUri.path} '
        '${html.length} chars',
      );
      for (final needle in [r'$CONFIG', 'idstr', '"uid"', 'uid:', 'oid']) {
        final i = html.indexOf(needle);
        final snippet = i < 0
            ? 'absent'
            : _redact(
                html.substring(max(0, i - 40), min(html.length, i + 120)),
              );
        _log('  $needle: $snippet');
      }
    } on Exception catch (e) {
      _log('page $url: $e');
    }
  }

  static String _redact(String s) => s
      .replaceAll(RegExp(r'\d{7,}'), '<num>')
      .replaceAll(RegExp(r'[^\x00-\x7F]+'), '<text>')
      .replaceAll(RegExp(r'\s+'), ' ');

  static Iterable<Map<String, Object?>> _maps(Object? list) =>
      list is List ? list.whereType<Map<String, Object?>>() : const [];

  static bool _truthy(Object? value) => switch (value) {
    null || false || 0 || '' || '0' => false,
    Map(isEmpty: true) || List(isEmpty: true) => false,
    _ => true,
  };

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('开发者诊断'),
        actions: [
          IconButton(
            tooltip: '运行',
            icon: const Icon(Icons.play_arrow),
            onPressed: _running ? null : _run,
          ),
        ],
      ),
      body: _lines.isEmpty
          ? Center(child: Text(_running ? '运行中…' : '点击右上角运行检查'))
          : ListView(
              padding: const EdgeInsets.all(12),
              children: [
                SelectableText(
                  _lines.join('\n'),
                  style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                ),
                if (_running) const LinearProgressIndicator(),
              ],
            ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

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
/// The report holds field names, counts and cookie names, never cookie
/// values or post content; the user's uid is masked. Each line is also
/// printed with a `[maxbo-diag]` prefix so it can be read from the device log.
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
    setState(() => _lines.add(line));
  }

  Future<void> _run() async {
    final session = ref.read(sessionProvider);
    if (session is! SignedIn) return;
    final uid = session.account.uid;
    String mask(String s) => s.replaceAll(uid, '<uid>');

    setState(() {
      _lines.clear();
      _running = true;
    });

    // A separate client without the expiry callback: an odd answer to an
    // experimental request must not sign the user out.
    final jar = ref.read(cookieJarProvider);
    final throttle = ref.read(requestThrottleProvider);
    final api = WeiboApi(createWeiboDio(jar: jar, throttle: throttle));

    Future<Map<String, Object?>?> fetch(
      String label,
      String path, [
      Map<String, Object?>? query,
      WeiboApi? client,
    ]) async {
      try {
        return await (client ?? api).getJson(
          'https://weibo.com$path',
          query: query,
        );
      } on Exception catch (e) {
        _log('$label: $e');
        return null;
      }
    }

    _log('== 1/6 Cookies');
    for (final site in ['weibo.com', 'weibo.cn']) {
      _log('jar $site: ${jar.names(site).join(', ')}');
    }
    try {
      final web = await ref.read(cookieBridgeProvider).readWeiboCookies();
      web.forEach((site, cookies) {
        _log('webview $site: ${cookies.keys.join(', ')}');
      });
    } on Exception catch (e) {
      _log('webview cookies: $e');
    }

    _log('== 4 /ajax/config');
    final config = await fetch('config', '/ajax/config');
    if (config != null) _log(mask(describeShape(config)));

    _log('== 2 UA binding');
    final mobileApi = WeiboApi(
      createWeiboDio(jar: jar, throttle: throttle, userAgent: _mobileUserAgent),
    );
    final mobile = await fetch('mobile UA', '/ajax/config', null, mobileApi);
    if (mobile != null) _log('mobile UA: ok=${mobile['ok']}');

    _log('== 3 groups / list_id');
    final groups = await fetch('allGroups', '/ajax/feed/allGroups', {
      'is_new_segment': 1,
      'fetch_hot': 1,
    });
    final gids = <String>[];
    if (groups != null) {
      _log(describeShape(groups, maxDepth: 5));
      _collect(groups, 'gid', gids);
      _log('gids: ${gids.take(8).map(mask).join(', ')}');
    }

    final variants = <String, String?>{
      'no list_id': null,
      'list_id=11000<uid>': '11000$uid',
      for (final gid in gids.take(2)) 'list_id=${mask(gid)}': gid,
    };
    var statuses = <Map<String, Object?>>[];
    for (final MapEntry(key: label, value: listId) in variants.entries) {
      final feed = await fetch(label, '/ajax/feed/friendstimeline', {
        'refresh': 4,
        'count': 20,
        'list_id': ?listId,
      });
      if (feed == null) continue;
      final items = (feed['statuses'] as List? ?? const [])
          .whereType<Map<String, Object?>>()
          .toList();
      _log(
        '$label: ok=${feed['ok']} statuses=${items.length} '
        'max_id=${feed['max_id'] != null} since_id=${feed['since_id'] != null}',
      );
      if (items.length > statuses.length) statuses = items;
    }

    _log('== 5 ad markers (${statuses.length} statuses)');
    for (final flag in _adFlags) {
      final count = statuses.where((s) => _truthy(s[flag])).length;
      _log('$flag: $count');
    }
    for (final label in _adLabels) {
      final values = {
        for (final s in statuses)
          if (s[label] case final String v when v.isNotEmpty)
            v.length > 40 ? '${v.substring(0, 40)}…' : v,
      };
      _log('$label: ${values.join(' | ')}');
    }
    final keys = {for (final s in statuses) ...s.keys}.toList()..sort();
    _log('status keys: ${keys.join(', ')}');

    _log('== done');
    if (mounted) setState(() => _running = false);
  }

  static void _collect(Object? json, String key, List<String> out) {
    switch (json) {
      case Map():
        for (final MapEntry(key: k, :value) in json.entries) {
          if (k == key && value != null) out.add('$value');
          _collect(value, key, out);
        }
      case List():
        for (final item in json) {
          _collect(item, key, out);
        }
    }
  }

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

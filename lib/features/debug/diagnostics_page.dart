import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/cookie_jar.dart';
import '../../core/net/request_throttle.dart';
import '../../core/net/weibo_dio.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/format.dart';
import '../../core/util/json_read.dart';
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

  /// Logs the structure of two timeline pages: how paging cursors relate to
  /// the statuses, and the shapes of the fields the status card renders.
  Future<void> _probeTimeline() async {
    setState(() {
      _lines.clear();
      _running = true;
    });
    final session = ref.read(sessionProvider);
    if (session is! SignedIn) {
      _log('timeline probe needs a signed-in session');
      setState(() => _running = false);
      return;
    }
    final uid = session.account.uid;
    final api = WeiboApi(
      createWeiboDio(
        jar: ref.read(cookieJarProvider),
        throttle: ref.read(requestThrottleProvider),
      ),
    );
    const url = 'https://weibo.com/ajax/feed/friendstimeline';
    final listId = '11000$uid';

    Future<List<Map<String, Object?>>?> page(
      String label,
      Map<String, Object?> cursor,
    ) async {
      final Map<String, Object?> feed;
      try {
        feed = await api.getJson(
          url,
          query: {'list_id': listId, 'refresh': 4, 'count': 20, ...cursor},
        );
      } on Exception catch (e) {
        _log('$label: $e');
        return null;
      }
      final statuses = _maps(feed['statuses']).toList();
      final first = statuses.isEmpty ? null : '${statuses.first['idstr']}';
      final last = statuses.isEmpty ? null : '${statuses.last['idstr']}';
      final maxId = '${feed['max_id']}';
      final sinceId = '${feed['since_id']}';
      _log(
        '$label: ${describeShape({...feed, 'statuses': null}, maxDepth: 1)}',
      );
      _log(
        '$label: statuses=${statuses.length} '
        'max_id=${feed['max_id'].runtimeType} '
        'cmp(max_id,last)=${_compareIds(maxId, last)} '
        'since_id=${feed['since_id'].runtimeType} '
        'cmp(since_id,first)=${_compareIds(sinceId, first)} '
        'max_id==0:${maxId == '0'}',
      );
      _cursors[label] = maxId;
      return statuses;
    }

    _log('== paging');
    final page1 = await page('page1', {'since_id': 0}) ?? const [];
    final page2 = page1.isEmpty
        ? const <Map<String, Object?>>[]
        : await page('page2', {'max_id': _cursors['page1']}) ?? const [];
    final ids1 = {for (final s in page1) '${s['idstr']}'};
    _log(
      'page2 overlaps page1: '
      '${page2.where((s) => ids1.contains('${s['idstr']}')).length}',
    );
    final all = [...page1, ...page2];
    final retweets = [
      for (final s in all)
        if (s['retweeted_status'] case final Map<String, Object?> r) r,
    ];
    final everything = [...all, ...retweets];

    _log('== status');
    _log('retweets=${retweets.length} of ${all.length}');
    _log('created_at: ${_patterns(everything.map((s) => s['created_at']))}');
    _log('source: ${_patterns(everything.map((s) => s['source']))}');
    for (final key in [
      'mblogtype',
      'isLongText',
      'pic_num',
      'visible',
      'title',
      'continue_tag',
      'mark',
      'isAd',
      'readtimetype',
    ]) {
      final shapes = {
        for (final s in everything)
          if (s.containsKey(key)) describeShape(s[key], maxDepth: 2),
      };
      _log('$key: ${shapes.join(' | ')}');
    }
    final retweetKeys = {for (final r in retweets) ...r.keys}.toList()..sort();
    _log('retweet keys: ${retweetKeys.join(', ')}');
    final deleted = retweets.where((r) => r['user'] == null).toList();
    _log(
      'retweets without user: ${deleted.length} '
      'keys=${{for (final r in deleted) ...r.keys}.join(',')}',
    );

    _log('== user');
    final users = [
      for (final s in everything)
        if (s['user'] case final Map<String, Object?> u) u,
    ];
    _log(
      'user keys: ${({for (final u in users) ...u.keys}.toList()..sort()).join(', ')}',
    );
    for (final key in [
      'profile_image_url',
      'avatar_large',
      'avatar_hd',
      'verified_type',
      'mbrank',
      'mbtype',
      'icon_list',
    ]) {
      _log(
        '$key: ${_patterns(users.map((u) => u[key] is String ? _urlPattern(u[key]! as String) : describeShape(u[key], maxDepth: 2)))}',
      );
    }

    _log('== pictures');
    final picTypes = <String>{};
    final variantPatterns = <String>{};
    final picKeys = <String>{};
    for (final s in everything) {
      final infos = s['pic_infos'];
      if (infos is! Map) continue;
      for (final info in infos.values.whereType<Map<String, Object?>>()) {
        picKeys.addAll(info.keys);
        picTypes.add('${info['type']}');
        for (final variant in [
          'thumbnail',
          'bmiddle',
          'large',
          'largest',
          'mw2000',
          'original',
        ]) {
          if (info[variant] case {'url': final String u}) {
            variantPatterns.add('$variant=${_urlPattern(u)}');
          }
        }
      }
    }
    _log('pic types: ${picTypes.join(', ')}');
    _log('pic keys: ${(picKeys.toList()..sort()).join(', ')}');
    for (final p in variantPatterns.toList()..sort()) {
      _log('  $p');
    }
    final mixed = everything.where((s) => s['mix_media_info'] != null);
    _log('mix_media_info: ${mixed.length}');

    _log('== page_info');
    for (final s in everything) {
      final info = s['page_info'];
      if (info is! Map<String, Object?>) continue;
      // Logcat cuts lines at ~4 KB, so log keys and the media fields only.
      _log(
        'object_type=${info['object_type']} type=${info['type']} '
        'keys=${info.keys.join(',')}',
      );
      for (final key in ['page_pic', 'pic_info', 'page_url']) {
        if (info[key] case final Object v) {
          _log(
            '  $key: ${v is String ? _urlPattern(v) : describeShape(v, maxDepth: 2)}',
          );
        }
      }
      if (info['media_info'] case final Map<String, Object?> media) {
        _log('  media_info keys=${media.keys.join(',')}');
        for (final e in media.entries) {
          if (e.value is String && '${e.value}'.startsWith('http')) {
            _log('  media.${e.key}: ${_urlPattern('${e.value}')}');
          }
        }
        _log(
          '  media.duration=${media['duration'].runtimeType} '
          'big_pic_info=${describeShape(media['big_pic_info'], maxDepth: 2)}',
        );
      }
    }

    _log('== text html');
    _logHtml(everything.map((s) => s['text']).whereType<String>());
    final urlStructs = [
      for (final s in everything)
        if (s['url_struct'] case final List l) ...l,
    ];
    _log('url_struct: ${urlStructs.length}');
    for (final u in urlStructs.whereType<Map<String, Object?>>().take(5)) {
      _log(
        '  keys=${u.keys.join(',')} '
        'short=${_urlPattern('${u['short_url']}')} '
        'long=${_urlPattern('${u['long_url'] ?? u['ori_url']}')}',
      );
    }
    _log('== done');
    if (mounted) setState(() => _running = false);
  }

  /// Logs the structure of what the detail page reads, using statuses from
  /// the first timeline page as samples: long text, fresh video URLs,
  /// comments in both orders with a reply thread, and reposts.
  Future<void> _probeDetail() async {
    setState(() {
      _lines.clear();
      _running = true;
    });
    final session = ref.read(sessionProvider);
    if (session is! SignedIn) {
      _log('detail probe needs a signed-in session');
      setState(() => _running = false);
      return;
    }
    final uid = session.account.uid;
    final api = WeiboApi(
      createWeiboDio(
        jar: ref.read(cookieJarProvider),
        throttle: ref.read(requestThrottleProvider),
      ),
    );

    Future<Map<String, Object?>?> fetch(
      String label,
      String path,
      Map<String, Object?> query,
    ) async {
      try {
        return await api.getJson('https://weibo.com/ajax/$path', query: query);
      } on Exception catch (e) {
        _log('$label: $e');
        return null;
      }
    }

    final feed = await fetch('timeline', 'feed/friendstimeline', {
      'list_id': '11000$uid',
      'refresh': 4,
      'count': 20,
      'since_id': 0,
    });
    final statuses = _maps(feed?['statuses']).toList();
    final everything = [
      ...statuses,
      for (final s in statuses)
        if (s['retweeted_status'] case final Map<String, Object?> r) r,
    ];
    Map<String, Object?>? most(String key) => everything.fold(
      null,
      (best, s) => best == null || _int(s[key]) > _int(best[key]) ? s : best,
    );
    final longs = everything
        .where(
          (s) =>
              s['isLongText'] == true ||
              '${s['text']}'.contains('class="expand"'),
        )
        .toList();
    final video = everything
        .where((s) => _pageInfo(s)?['object_type'] == 'video')
        .firstOrNull;
    final commented = most('comments_count');
    final reposted = most('reposts_count');
    _log(
      'samples of ${everything.length}: long=${longs.length} '
      'video=${video != null} '
      'max comments=${commented?['comments_count']} '
      'max reposts=${reposted?['reposts_count']}',
    );

    _log('== long text (${longs.length} of ${everything.length})');
    for (final long in longs.take(3)) {
      final show = await fetch('show(id)', 'statuses/show', {
        'id': '${long['idstr']}',
        'isGetLongText': 'true',
      });
      final longText = await fetch('longtext', 'statuses/longtext', {
        'id': '${long['mblogid']}',
      });
      final data = readMap(longText?['data']);
      final content = readString(data?['longTextContent']) ?? '';
      // The last phrase of the full text tells a complete text from a
      // truncated one without logging either.
      final tail = RegExp(r'[\u4e00-\u9fff]{4,}')
          .allMatches(content)
          .lastOrNull?[0];
      bool hasTail(Object? text) => tail != null && '$text'.contains(tail);
      _log(
        'timeline: isLongText=${long['isLongText']} ${_htmlStats(long['text'])} '
        'tail=${hasTail(long['text'])} raw=${'${long['text_raw']}'.length}',
      );
      _log(
        'show: isLongText=${show?['isLongText']} ${_htmlStats(show?['text'])} '
        'tail=${hasTail(show?['text'])} raw=${'${show?['text_raw']}'.length} '
        'longText=${describeShape(show?['longText'], maxDepth: 0)}',
      );
      _log(
        'longtext: ${describeShape(data, maxDepth: 1)} '
        '${_htmlStats(content)} same as show text_raw=${content == show?['text_raw']}',
      );
    }

    _log('== video');
    if (video != null) {
      _logMedia('timeline', video);
      final show = await fetch('show(mblogid)', 'statuses/show', {
        'id': '${video['mblogid']}',
      });
      if (show != null) {
        _log(
          'show(mblogid): ok=${show['ok']} same id=${show['idstr'] == video['idstr']}',
        );
        _logMedia('show', show);
      }
    }
    final mixed = everything.where((s) => s['mix_media_info'] != null);
    for (final s in mixed.take(2)) {
      _log(
        'mix_media_info: ${describeShape(s['mix_media_info'], maxDepth: 3)}',
      );
    }

    _log('== pictures by type');
    final picKeys = <String, Set<String>>{};
    final picUrls = <String>{};
    for (final s in everything) {
      if (s['pic_infos'] case final Map<String, Object?> infos) {
        for (final info in infos.values.whereType<Map<String, Object?>>()) {
          final type = '${info['type']}';
          (picKeys[type] ??= {}).addAll(info.keys);
          if (type == 'pic') continue;
          for (final e in info.entries) {
            if (e.value case {'url': final String url}) {
              picUrls.add('$type.${e.key}=${_urlPattern(url)}');
            } else if (e.value is String && '${e.value}'.startsWith('http')) {
              picUrls.add('$type.${e.key}=${_urlPattern('${e.value}')}');
            }
          }
        }
      }
    }
    picKeys.forEach((type, keys) {
      _log('$type keys: ${(keys.toList()..sort()).join(',')}');
    });
    for (final p in picUrls.toList()..sort()) {
      _log('  $p');
    }

    _log('== comments');
    final comments = <Map<String, Object?>>[];
    if (commented != null) {
      final id = '${commented['idstr']}';
      final author = '${readMap(commented['user'])?['idstr']}';
      for (final flow in [0, 1]) {
        await _pages('flow=$flow', comments, (cursor) {
          return fetch('flow=$flow', 'statuses/buildComments', {
            'is_reload': 1,
            'id': id,
            'is_show_bulletin': 2,
            'is_mix': 0,
            'count': 20,
            'uid': author,
            'fetch_level': 0,
            'flow': flow,
            'locale': 'zh-CN',
            'max_id': ?cursor,
          });
        });
      }
      _logComments(comments);

      final root = comments.fold<Map<String, Object?>?>(
        null,
        (best, c) =>
            _int(c['total_number']) > _int(best?['total_number']) ? c : best,
      );
      _log('== replies (root has ${root?['total_number']})');
      if (root != null) {
        final replies = <Map<String, Object?>>[];
        await _pages('replies', replies, (cursor) {
          return fetch('replies', 'statuses/buildComments', {
            'is_reload': 1,
            'id': '${root['idstr'] ?? root['id']}',
            'is_show_bulletin': 2,
            'is_mix': 1,
            'fetch_level': 1,
            'max_id': cursor ?? 0,
            'count': 20,
            'uid': author,
            'locale': 'zh-CN',
          });
        });
        _logComments(replies);
      }
    }

    _log('== reposts');
    if (reposted != null) {
      final reposts = <Map<String, Object?>>[];
      for (final page in [1, 2]) {
        final json = await fetch('reposts p$page', 'statuses/repostTimeline', {
          'id': '${reposted['idstr']}',
          'page': page,
          'moduleID': 'feed',
          'count': 10,
        });
        if (json == null) break;
        final items = _maps(json['data']).toList();
        final seen = {for (final r in reposts) '${r['idstr']}'};
        _log('p$page: ${describeShape({...json, 'data': null}, maxDepth: 1)}');
        _log(
          'p$page: items=${items.length} '
          'overlap=${items.where((r) => seen.contains('${r['idstr']}')).length} '
          'total_number=${json['total_number']} max_page=${json['max_page']} '
          'order=${_order(items)}',
        );
        reposts.addAll(items);
        if (items.isEmpty) break;
      }
      _log(
        'repost keys: '
        '${({for (final r in reposts) ...r.keys}.toList()..sort()).join(', ')}',
      );
      _log(
        'repost retweeted_status: '
        '${reposts.where((r) => r['retweeted_status'] != null).length}',
      );
      _logHtml(reposts.map((r) => r['text']).whereType<String>());
    }
    _log('== done');
    if (mounted) setState(() => _running = false);
  }

  /// Fetches up to two pages of a `max_id`-cursored comment list into [into],
  /// logging how the cursor and pages relate.
  Future<void> _pages(
    String label,
    List<Map<String, Object?>> into,
    Future<Map<String, Object?>?> Function(String? cursor) fetchPage,
  ) async {
    String? cursor;
    final seen = <String>{};
    for (final n in [1, 2]) {
      final json = await fetchPage(cursor);
      if (json == null) return;
      final items = _maps(json['data']).toList();
      _log(
        '$label p$n: ${describeShape({...json, 'data': null}, maxDepth: 1)}',
      );
      _log(
        '$label p$n: items=${items.length} '
        'overlap=${items.where((c) => !seen.add('${c['id']}')).length} '
        'max_id=${json['max_id'].runtimeType} '
        'max_id==0:${'${json['max_id']}' == '0'} '
        'total_number=${json['total_number']} order=${_order(items)}',
      );
      into.addAll(items);
      cursor = '${json['max_id']}';
      if (items.isEmpty || cursor == '0' || json['max_id'] == null) return;
    }
  }

  void _logComments(List<Map<String, Object?>> comments) {
    final keys = {for (final c in comments) ...c.keys}.toList()..sort();
    _log('comment keys: ${keys.join(', ')}');
    for (final key in [
      'id',
      'rootid',
      'total_number',
      'like_counts',
      'liked',
      'floor_number',
      'comments',
      'reply_comment',
      'pic',
      'more_info',
      'url_struct',
      'isLikedByMblogAuthor',
      'readtimetype',
    ]) {
      final shapes = {
        for (final c in comments)
          if (c.containsKey(key)) describeShape(c[key], maxDepth: 2),
      };
      _log('$key: ${shapes.take(4).join(' | ')}');
    }
    _log('created_at: ${_patterns(comments.map((c) => c['created_at']))}');
    _log('source: ${_patterns(comments.map((c) => c['source']))}');
    final inline = [
      for (final c in comments)
        if (c['comments'] case final List l) l.length,
    ];
    _log(
      'inline replies per comment: ${inline.toSet().join(',')} '
      'vs total_number>0: '
      '${comments.where((c) => _int(c['total_number']) > 0).length}',
    );
    final texts = comments.map((c) => c['text']).whereType<String>();
    _log(
      'text starting 回复: ${texts.where((t) => t.startsWith('回复')).length}'
      '/${texts.length}',
    );
    _logHtml(texts);
    for (final c in comments) {
      for (final u in readMaps(c['url_struct'])) {
        final short = '${u['short_url']}';
        _log(
          'url_struct: object_type=${u['object_type']} '
          'url_type=${u['url_type']} title_len=${'${u['url_title']}'.length} '
          'pics=${describeShape(u['pic_ids'], maxDepth: 1)} '
          'gif=${u['vip_gif'] != null} '
          'short in text=${'${c['text']}'.contains(short)} '
          'in text_raw=${'${c['text_raw']}'.contains(short)} '
          'pic_num=${c['pic_num']}',
        );
        for (final info in readMap(u['pic_infos'])?.values.take(1) ?? []) {
          final pic = readMap(info);
          _log('  pic keys: ${pic?.keys.join(',')} type=${pic?['type']}');
          for (final key in [
            'thumbnail',
            'bmiddle',
            'large',
            'original',
            'largest',
            'mw2000',
          ]) {
            if (readMap(pic?[key])?['url'] case final String url) {
              _log('  $key: ${_urlPattern(url)}');
            }
          }
        }
      }
    }
  }

  /// Logs the media URLs of a video status and how long until they expire.
  void _logMedia(String label, Map<String, Object?> status) {
    final media = readMap(_pageInfo(status)?['media_info']);
    if (media == null) {
      _log('$label: no media_info');
      return;
    }
    _log('$label media_info keys: ${(media.keys.toList()..sort()).join(',')}');
    for (final e in media.entries) {
      if (e.value is String && '${e.value}'.startsWith('http')) {
        _log('  ${e.key}: ${_urlPattern('${e.value}')} ${_expiry(e.value)}');
      }
    }
    for (final item in _maps(media['playback_list'])) {
      final meta = readMap(item['meta']);
      final play = readMap(item['play_info']);
      _log(
        '  playback: label=${meta?['label']} '
        'quality=${meta?['quality_index']}/${meta?['quality_desc']} '
        'type=${meta?['type']} mime=${play?['mime']} '
        'protocol=${play?['protocol']} '
        '${play?['width']}x${play?['height']} '
        '${_urlPattern('${play?['url']}')} ${_expiry(play?['url'])}',
      );
    }
  }

  static String _expiry(Object? url) {
    final query = Uri.tryParse('$url')?.queryParameters ?? const {};
    final expires = int.tryParse(query['Expires'] ?? '');
    if (expires == null) return 'no Expires';
    final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    return 'expires in ${(expires - now) ~/ 60} min';
  }

  /// Length, line breaks and whether [text] holds markup; never the text.
  static String _htmlStats(Object? text) {
    if (text is! String) return '(${text.runtimeType})';
    return '${text.length} chars, '
        '${'\n'.allMatches(text).length} newlines, '
        '${'<br'.allMatches(text).length} <br>, '
        '${'<a '.allMatches(text).length} <a>, '
        'expand=${text.contains('class="expand"')}';
  }

  /// Whether [items] run newest first by `created_at`.
  static String _order(List<Map<String, Object?>> items) {
    final times = [
      for (final i in items) ?parseWeiboDate(readString(i['created_at'])),
    ];
    var desc = true;
    var asc = true;
    for (var i = 1; i < times.length; i++) {
      if (times[i].isAfter(times[i - 1])) desc = false;
      if (times[i].isBefore(times[i - 1])) asc = false;
    }
    return times.length < 2
        ? 'n/a'
        : desc
        ? 'newest first'
        : asc
        ? 'oldest first'
        : 'mixed';
  }

  static Map<String, Object?>? _pageInfo(Map<String, Object?> status) =>
      readMap(status['page_info']);

  static int _int(Object? value) => readInt(value) ?? 0;

  /// Logs the distinct tags (with attribute names and classes) and link
  /// shapes in [texts], never the text itself.
  void _logHtml(Iterable<String> texts) {
    final tags = <String>{};
    final hrefs = <String>{};
    for (final text in texts) {
      for (final m in RegExp(r'<(\w+)([^>]*)>').allMatches(text)) {
        final attrs = RegExp(r'(\w[\w-]*)=')
            .allMatches(m[2]!)
            .map((a) => a[1])
            .join(',');
        final cls = RegExp(r'class="?([\w -]+)').firstMatch(m[2]!)?[1];
        tags.add('${m[1]}[$attrs]${cls == null ? '' : '.$cls'}');
        final href = RegExp(r'''href=["']?([^"'\s>]+)''').firstMatch(m[2]!);
        if (href != null) {
          // Mention links carry the user's name; keep only the shape.
          hrefs.add(
            href[1]!.startsWith('/n/') ? '/n/<name>' : _urlPattern(href[1]!),
          );
        }
      }
    }
    for (final t in tags.toList()..sort()) {
      _log('  tag $t');
    }
    for (final h in hrefs.toList()..sort()) {
      _log('  href $h');
    }
  }

  final _cursors = <String, String>{};

  /// How two numeric ids compare, without revealing them.
  static String _compareIds(String a, String? b) {
    final x = BigInt.tryParse(a);
    final y = b == null ? null : BigInt.tryParse(b);
    if (x == null || y == null) return 'n/a';
    final c = x.compareTo(y);
    return c == 0 ? '=' : (c < 0 ? '<' : '>');
  }

  /// A URL reduced to its host and path shape: query values, digits and
  /// file names are masked.
  static String _urlPattern(String url) {
    final uri = Uri.tryParse(url.startsWith('//') ? 'https:$url' : url);
    if (uri == null) return '<bad url>';
    final segments = uri.pathSegments;
    final path = [
      for (final (i, seg) in segments.indexed)
        i == segments.length - 1 && seg.contains('.')
            ? '<file>.${seg.split('.').last}'
            : _redact(seg).replaceAll(RegExp(r'\d+'), '9'),
    ].join('/');
    final query = uri.queryParameters.keys.join('&');
    return '${uri.host}/$path${query.isEmpty ? '' : '?$query'}';
  }

  /// Distinct value shapes: digits become 9, non-ASCII text is masked.
  static String _patterns(Iterable<Object?> values) => {
    for (final v in values)
      v is String
          ? _redact(v).replaceAll(RegExp(r'\d'), '9')
          : '${v.runtimeType}',
  }.take(6).join(' | ');

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
            tooltip: '详情结构',
            icon: const Icon(Icons.article_outlined),
            onPressed: _running ? null : _probeDetail,
          ),
          IconButton(
            tooltip: '时间线结构',
            icon: const Icon(Icons.view_agenda_outlined),
            onPressed: _running ? null : _probeTimeline,
          ),
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

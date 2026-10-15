import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/net/cookie_jar.dart';
import '../../core/net/request_throttle.dart';
import '../../core/net/weibo_dio.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/format.dart';
import '../../core/util/json_read.dart';
import '../../core/util/json_shape.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/comment.dart';
import '../../data/models/status.dart';

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

  /// Logs the structure of what the profile and hot search pages read: user
  /// profiles looked up by uid and by screen name, how a user's statuses
  /// page, and the hot search list.
  Future<void> _probeProfile() async {
    setState(() {
      _lines.clear();
      _running = true;
    });
    final session = ref.read(sessionProvider);
    if (session is! SignedIn) {
      _log('profile probe needs a signed-in session');
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

    // Another user to look at: the author with the most posts on the first
    // timeline page who isn't the signed-in user.
    final feed = await fetch('timeline', 'feed/friendstimeline', {
      'list_id': '11000$uid',
      'refresh': 4,
      'count': 20,
      'since_id': 0,
    });
    final authors = <String, Map<String, Object?>>{};
    final posts = <String, int>{};
    for (final s in _maps(feed?['statuses'])) {
      final user = readMap(s['user']);
      final id = readString(user?['idstr']);
      if (user == null || id == null || id == uid) continue;
      authors[id] = user;
      posts[id] = (posts[id] ?? 0) + 1;
    }
    final other =
        (posts.entries.toList()..sort((a, b) => b.value.compareTo(a.value)))
            .firstOrNull
            ?.key;
    _log('authors=${authors.length} other=${other != null}');

    void logUser(String label, Map<String, Object?>? json) {
      if (json == null) return;
      _log('$label: ${describeShape({...json, 'data': null}, maxDepth: 1)}');
      final data = readMap(json['data']);
      _log(
        '$label data: '
        '${describeShape({...?data, 'user': null}, maxDepth: 2)}',
      );
      final user = readMap(data?['user']);
      if (user == null) return;
      _log('$label user keys: ${(user.keys.toList()..sort()).join(', ')}');
      for (final key in [
        'id',
        'idstr',
        'description',
        'location',
        'gender',
        'followers_count',
        'followers_count_str',
        'friends_count',
        'statuses_count',
        'verified',
        'verified_type',
        'verified_reason',
        'following',
        'follow_me',
        'cover_image_phone',
        'avatar_hd',
        'avatar_large',
        'profile_image_url',
        'mbrank',
        'svip',
        'user_type',
        'is_muteuser',
        'special_follow',
      ]) {
        final v = user[key];
        _log(
          '  $key: ${v is String && v.startsWith('http')
              ? _urlPattern(v)
              : v is String
              ? 'str(${v.length})'
              : describeShape(v, maxDepth: 1)}',
        );
      }
    }

    _log('== profile by uid (self)');
    final selfProfile = await fetch('self', 'profile/info', {'uid': uid});
    logUser('self', selfProfile);

    if (other != null) {
      _log('== profile by uid (other)');
      final byUid = await fetch('other', 'profile/info', {'uid': other});
      logUser('other', byUid);

      _log('== profile by screen_name');
      final name = readString(authors[other]?['screen_name']);
      final byName = name == null
          ? null
          : await fetch('by name', 'profile/info', {'screen_name': name});
      if (byName != null) {
        final user = readMap(readMap(byName['data'])?['user']);
        _log(
          'by name: ok=${byName['ok']} same idstr=${user?['idstr'] == other} '
          '${describeShape({...byName, 'data': null}, maxDepth: 1)}',
        );
      }
      final missing = await fetch('missing name', 'profile/info', {
        'screen_name': 'maxbo_no_such_user_zq9x',
      });
      if (missing != null) _log('missing name: ${describeShape(missing)}');

      _log('== mymblog (other)');
      await _probeUserStatuses(fetch, other, pages: 2);
    }
    _log(
      '== mymblog (self, statuses_count=${readMap(readMap(selfProfile?['data'])?['user'])?['statuses_count']})',
    );
    await _probeUserStatuses(fetch, uid, pages: 3);

    _log('== hotSearch');
    final hot = await fetch('hotSearch', 'side/hotSearch', {});
    if (hot != null) {
      final data = readMap(hot['data']);
      _log('hotSearch: ${describeShape({...hot, 'data': null}, maxDepth: 1)}');
      _log('data: ${describeShape(data, maxDepth: 1)}');
      for (final entry in data?.entries ?? <MapEntry<String, Object?>>[]) {
        final items = entry.value is Map
            ? [readMap(entry.value)!]
            : _maps(entry.value).toList();
        if (items.isEmpty) continue;
        final keys = {for (final i in items) ...i.keys}.toList()..sort();
        _log('${entry.key}: ${items.length} items, keys: ${keys.join(', ')}');
        for (final key in [
          'word',
          'word_scheme',
          'note',
          'num',
          'rank',
          'realpos',
          'label_name',
          'icon_desc',
          'icon_desc_color',
          'small_icon_desc',
          'small_icon_desc_color',
          'flag',
          'is_ad',
          'ad_type',
          'topic_flag',
          'icon',
          'mid',
          'url',
          'name',
          'pos',
        ]) {
          final values = <String>{
            for (final i in items)
              if (i.containsKey(key))
                switch (i[key]) {
                  // Labels are Weibo's one-character badges such as 新/热.
                  final String v
                      when key.contains('label') || key.contains('icon_desc') =>
                    v,
                  final String v when v.startsWith('http') => _urlPattern(v),
                  final String v => 'str(${v.length})',
                  final v => describeShape(v, maxDepth: 1),
                },
          };
          if (values.isNotEmpty) {
            _log('  $key: ${values.take(8).join(' | ')}');
          }
        }
        final scheme = items
            .where(
              (i) =>
                  i['word_scheme'] is String &&
                  i['word'] is String &&
                  i['word_scheme'] != i['word'],
            )
            .map(
              (i) => '${i['word_scheme']}' == '#${i['word']}#'
                  ? '#word#'
                  : 'other',
            )
            .toSet();
        _log('  word_scheme vs word: ${scheme.join(', ')}');
        _log(
          '  ads: ${items.where((i) => _truthy(i['is_ad'])).length} '
          'ranks: ${[for (final i in items.take(60)) i['rank'] ?? '-'].join(',')}',
        );
        _log(
          '  realpos: '
          '${[for (final i in items.take(60)) i['realpos'] ?? '-'].join(',')}',
        );
        for (final (n, i) in items.indexed) {
          final ad = _truthy(i['is_ad']) || _truthy(i['topic_ad']);
          if (!ad && n > 2) continue;
          final word = '${i['word'] ?? ''}';
          _log(
            '  [$n] is_ad=${i['is_ad']} topic_ad=${i['topic_ad']} '
            'rank=${i['rank']} realpos=${i['realpos']} num=${i['num']} '
            'flag=${i['flag']} icon_type=${i['icon_type']} '
            'label=${i['label_name']}/${i['icon_desc']}/${i['small_icon_desc']} '
            'flag_desc=${describeShape(i['flag_desc'], maxDepth: 0)} '
            'emoticon=${describeShape(i['emoticon'], maxDepth: 1)} '
            'dot_icon=${describeShape(i['dot_icon'], maxDepth: 0)} '
            'note==word=${i['note'] == i['word']} '
            'word=${word.startsWith('#') ? '#…' : '…'}${word.endsWith('#') ? '#' : ''} '
            'scheme=${'${i['word_scheme']}' == '#$word#'
                ? '#word#'
                : '${i['word_scheme']}' == word
                ? 'word'
                : 'other'}',
          );
        }
      }
    }
    _log('== done');
    if (mounted) setState(() => _running = false);
  }

  /// Read-only: the unread counts and the lists behind weibo.com's 消息
  /// page (mentions, comments, likes), and which list clears which count.
  ///
  /// Logs shapes, counts and cursors only, never names or text.
  Future<void> _probeNotifications() async {
    setState(() {
      _lines.clear();
      _running = true;
    });
    if (ref.read(sessionProvider) is! SignedIn) {
      _log('notifications probe needs a signed-in session');
      setState(() => _running = false);
      return;
    }
    final dio = createWeiboDio(
      jar: ref.read(cookieJarProvider),
      throttle: ref.read(requestThrottleProvider),
    );
    final api = WeiboApi(dio);

    // weibo.com reads these with JSONP every 30 s, passing its own web
    // source id and trim_null=1 (which leaves out zero counts); we read them
    // once without a callback, with every count.
    Map<String, Object?>? previous;
    Future<void> unread(String label) async {
      try {
        final response = await dio.get<String>(
          'https://rm.api.weibo.com/2/remind/push_count.json',
          queryParameters: {
            'trim_null': 0,
            'with_dm_group': 1,
            'with_reminding': 1,
            'with_settings': 1,
            'exclude_attitude': 0,
            'with_chat_group_notice': 1,
            'source': 339644097,
            'with_chat_group': 1,
            'with_dm_unread': 1,
          },
          options: Options(responseType: ResponseType.plain),
        );
        final body = response.data ?? '';
        Object? json;
        try {
          json = jsonDecode(body);
        } on FormatException {
          json = null;
        }
        if (json is! Map<String, Object?>) {
          _log(
            'unread $label: ${response.statusCode} '
            '${response.headers.value('content-type')} not JSON, '
            '${body.length} chars, starts '
            '${_redact(body.substring(0, min(body.length, 40)))}',
          );
          return;
        }
        final counts = {
          for (final e in json.entries)
            if (e.value is int) e.key: e.value,
        };
        if (previous == null) {
          _log(
            'unread $label: ${response.statusCode} '
            '${response.headers.value('content-type')} '
            '${describeShape({for (final e in json.entries)
              if (e.value is! int) e.key: e.value}, maxDepth: 2)}',
          );
          _log(
            'unread counts: ${counts.entries.map((e) => '${e.key}=${e.value}').join(' ')}',
          );
        } else {
          final changed = {
            ...counts.keys,
            ...previous!.keys,
          }.where((k) => counts[k] != previous![k]);
          _log(
            'unread $label: '
            '${changed.isEmpty ? 'no change' : changed.map((k) => '$k ${previous![k]}->${counts[k]}').join(' ')}',
          );
        }
        previous = counts;
      } on DioException catch (e) {
        _log('unread $label: ${mapDioException(e)}');
      }
    }

    void logStatuses(String label, Iterable<Map<String, Object?>> statuses) {
      final list = statuses.toList();
      if (list.isEmpty) return;
      _log(
        '$label statuses: ${list.length}, '
        'parsed=${list.where((s) => Status.tryFromJson(s) != null).length} '
        'retweets=${list.where((s) => s['retweeted_status'] != null).length}',
      );
    }

    void logItems(String label, String key, List<Map<String, Object?>> items) {
      if (items.isEmpty) return;
      final keys = {for (final i in items) ...i.keys}.toList()..sort();
      _log('$label keys: ${keys.join(', ')}');
      for (final field in [
        'id',
        'idstr',
        'idStr',
        'created_at',
        'type',
        'attitude_type',
        'last_attitude',
        'source_type',
        'user',
        'status',
        'comment',
        'reply_comment',
        'object',
        'object_type',
        'attitude',
        'like_counts',
        'liked',
        'rootid',
        'menus',
      ]) {
        final shapes = {
          for (final i in items)
            if (i.containsKey(field))
              switch (i[field]) {
                final Map<String, Object?> m =>
                  '{${m.length} keys}${m.containsKey('status') ? '+status' : ''}',
                final String s
                    when field == 'created_at' || field == 'attitude' =>
                  _patterns([s]),
                final v => describeShape(v, maxDepth: 0),
              },
        };
        if (shapes.isNotEmpty) _log('  $field: ${shapes.take(4).join(' | ')}');
      }
      _log('  order: ${_order(items)}');
      switch (key) {
        case 'statuses':
          logStatuses(label, items);
        case 'comments':
          _log(
            '  Comment.tryFromJson: '
            '${items.where((c) => Comment.tryFromJson(c) != null).length}'
            '/${items.length}',
          );
          logStatuses(
            '$label .status',
            _maps([for (final c in items) c['status']]),
          );
          _log(
            '  .reply_comment: ${items.where((c) => c['reply_comment'] != null).length}'
            ' .status null: ${items.where((c) => c['status'] == null).length}',
          );
          _logHtml(items.map((c) => c['text']).whereType<String>());
        default:
          for (final field in ['status', 'comment', 'object']) {
            final inner = _maps([for (final i in items) i[field]]).toList();
            if (inner.isEmpty) continue;
            final innerKeys = {for (final i in inner) ...i.keys}.toList()
              ..sort();
            _log('  .$field keys: ${innerKeys.join(', ')}');
            if (field == 'status') logStatuses('$label .status', inner);
            if (field == 'comment') {
              _log(
                '  .comment Comment.tryFromJson: '
                '${inner.where((c) => Comment.tryFromJson(c) != null).length}'
                '/${inner.length}',
              );
              logStatuses(
                '$label .comment.status',
                _maps([for (final c in inner) c['status']]),
              );
            }
          }
      }
    }

    // One of the lists on the 消息 page: up to two pages, then the unread
    // counts again.
    Future<void> list(
      String label,
      String path,
      String key, [
      Map<String, Object?> params = const {},
    ]) async {
      _log('== $label ($path ${params.keys.join(',')})');
      final seen = <String>{};
      final all = <Map<String, Object?>>[];
      String? cursor;
      for (final n in [1, 2]) {
        final Map<String, Object?> json;
        try {
          json = await api.getJson(
            'https://weibo.com/ajax/$path',
            query: {...params, 'max_id': cursor ?? 0},
          );
        } on Exception catch (e) {
          _log('$label p$n: $e');
          break;
        }
        final data = readMap(json['data']);
        final items = _maps(data?[key]).toList();
        if (n == 1) {
          _log(
            '$label: ${describeShape({...json, 'data': null}, maxDepth: 1)}',
          );
          _log(
            '$label data: ${describeShape({...?data, key: null}, maxDepth: 1)}',
          );
        }
        _log(
          '$label p$n flags: '
          '${[
            for (final k in ['tips_show', 'miss_count', 'miss_rank', 'hasvisible', 'interval', 'extend_card', 'low_quality_rank'])
              if (data?.containsKey(k) ?? false) '$k=${data![k]}',
          ].join(' ')} '
          'miss_ids=${(data?['miss_ids'] as List?)?.length} '
          'pop_tips=${_patterns([data?['pop_tips']])} '
          'filter_hints=${_patterns([data?['filter_hints']])}',
        );
        final next = data?['next_cursor'];
        _log(
          '$label p$n: items=${items.length} '
          'overlap=${items.where((i) => !seen.add('${i['idstr'] ?? i['id']}')).length} '
          'next_cursor=${next.runtimeType} ==0:${'$next' == '0'} '
          'vs last id: ${_compareIds('$next', readString(items.lastOrNull?['idstr']) ?? '${items.lastOrNull?['id']}')} '
          'total_number=${data?['total_number']} messType=${data?['messType']}',
        );
        all.addAll(items);
        if (items.isEmpty || next == null || '$next' == '0') break;
        cursor = '$next';
      }
      logItems(label, key, all);
      await unread('after $label');
    }

    _log('== unread before');
    await unread('before');
    await list('@statuses', 'statuses/mentions', 'statuses');
    await list('@comments', 'comments/mentions', 'comments');
    await list('cmt inbox', 'message/cmt', 'comments');
    await list('cmt inbox (following)', 'message/cmt', 'comments', {
      'filter_by_author': 1,
    });
    await list('cmt outbox', 'message/myCmt', 'comments');
    await list('likes', 'message/attitudes', 'attitudes');

    _log('== msgList all');
    try {
      final json = await api.getJson(
        'https://weibo.com/ajax/message/msgList',
        query: {'type': 'all', 'max_id': 0, 'sinceId': ''},
      );
      final data = readMap(json['data']);
      final items = _maps(data?['list']).toList();
      _log(
        'msgList data: ${describeShape({...?data, 'list': null}, maxDepth: 1)}',
      );
      _log('msgList: items=${items.length}');
      for (final i in items.take(3)) {
        _log('  item: ${describeShape(i, maxDepth: 1)}');
      }
    } on Exception catch (e) {
      _log('msgList: $e');
    }
    _log('== done');
    dio.close();
    if (mounted) setState(() => _running = false);
  }

  /// Opens an app route typed in, e.g. `/n/name`, to reach pages the app
  /// has no way to yet.
  Future<void> _openRoute() async {
    // No controller: it would be disposed while the dialog animates out.
    var text = '';
    final route = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('打开页面'),
        content: TextField(
          autofocus: true,
          onChanged: (v) => text = v,
          decoration: const InputDecoration(hintText: '/n/昵称、/status/id …'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, text.trim()),
            child: const Text('打开'),
          ),
        ],
      ),
    );
    if (route != null && route.startsWith('/') && mounted) {
      unawaited(context.push(route));
    }
  }

  /// Asks which status to try likes and comments on: `@name` for the first
  /// status on that user's profile, or a status id or mblogid; and whether to
  /// try comments too.
  Future<({String target, bool comments})?> _askTarget() {
    var target = '';
    var comments = true;
    return showDialog<({String target, bool comments})>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: const Text('互动测试目标'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                autofocus: true,
                onChanged: (v) => target = v,
                decoration: const InputDecoration(hintText: '@昵称 或 微博 id'),
              ),
              CheckboxListTile(
                value: comments,
                onChanged: (v) => setState(() => comments = v ?? true),
                title: const Text('包括评论'),
                contentPadding: EdgeInsets.zero,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('取消'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, (
                target: target.trim(),
                comments: comments,
              )),
              child: const Text('开始'),
            ),
          ],
        ),
      ),
    );
  }

  /// Logs the ad-related fields of a user's statuses, to see why a normal
  /// post got the 广告 label (PLAN §11). Read-only; logs post dates and field
  /// values, never post text.
  Future<void> _probeAds() async {
    final session = ref.read(sessionProvider);
    if (session is! SignedIn) {
      setState(() => _lines.clear());
      _log('ad probe needs a signed-in session');
      return;
    }
    var text = '';
    final input = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('广告字段'),
        content: TextField(
          autofocus: true,
          onChanged: (v) => text = v,
          decoration: const InputDecoration(hintText: '@昵称 或 uid'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, text.trim()),
            child: const Text('开始'),
          ),
        ],
      ),
    );
    if (input == null || input.isEmpty || !mounted) return;
    setState(() {
      _lines.clear();
      _running = true;
    });
    final api = WeiboApi(
      createWeiboDio(
        jar: ref.read(cookieJarProvider),
        throttle: ref.read(requestThrottleProvider),
      ),
    );
    try {
      final user = input.startsWith('@')
          ? (await api.fetchProfile(screenName: input.substring(1))).user.id
          : input;
      final flagged = <Map<String, Object?>>[];
      final normal = <Map<String, Object?>>[];
      for (var page = 1; page <= 3; page++) {
        final json = await api.getJson(
          'https://weibo.com/ajax/statuses/mymblog',
          query: {'uid': user, 'page': page, 'feature': 0},
        );
        final list = _maps(readMap(json['data'])?['list']).toList();
        _log('p$page: ${list.length} statuses');
        for (final s in list) {
          final status = Status.tryFromJson(s);
          (status?.isAd ?? false ? flagged : normal).add(s);
          if (status?.isAd != true) continue;
          final date = status?.createdAt?.toIso8601String().substring(0, 10);
          final mark = '${s['mark'] ?? ''}';
          _log(
            '  $date isAd=${s['isAd']} readtimetype=${s['readtimetype']} '
            'own=${status?.user?.id == user} '
            'retweet=${s['retweeted_status'] != null} '
            'mblogtype=${s['mblogtype']} '
            'mblogtypename=${_redact('${s['mblogtypename'] ?? ''}')} '
            'mark=${_redact(mark.length > 60 ? '${mark.substring(0, 60)}…' : mark)}',
          );
        }
        if (list.isEmpty || readMap(json['data'])?['since_id'] == '') break;
      }
      // Fields that only flagged statuses have, or that they all share with
      // a value no normal status has.
      final flaggedKeys = {for (final s in flagged) ...s.keys};
      final normalKeys = {for (final s in normal) ...s.keys};
      _log('flagged=${flagged.length} normal=${normal.length}');
      _log('only flagged: ${flaggedKeys.difference(normalKeys).join(', ')}');
      for (final key in flaggedKeys.difference(normalKeys)) {
        _log(
          '  $key: ${{for (final s in flagged) describeShape(s[key], maxDepth: 3)}.join(' | ')}',
        );
      }
      for (final key in flaggedKeys.intersection(normalKeys)) {
        // Scalars by value (masked), anything else by shape.
        String value(Map<String, Object?> s) => switch (s[key]) {
          final bool v => '$v',
          final num v => _redact('$v'),
          final String v when v.length <= 40 => _redact(v),
          final v => describeShape(v, maxDepth: 2),
        };
        final a = {for (final s in flagged) value(s)};
        final b = {for (final s in normal) value(s)};
        if (a.length == 1 && !b.contains(a.single)) {
          _log('  $key: flagged=${a.single} normal=${b.take(3).join(' | ')}');
        }
      }
    } on Exception catch (e) {
      _log('ad probe: $e');
    }
    _log('== done');
    if (mounted) setState(() => _running = false);
  }

  /// Logs what liking and commenting answer, on one status the user picked
  /// for testing (PLAN §11): likes and takes back a like on it, comments,
  /// replies, likes the comment, then deletes both comments again. Leaves the
  /// status as it was.
  Future<void> _probeInteractions() async {
    final session = ref.read(sessionProvider);
    if (session is! SignedIn) {
      setState(() => _lines.clear());
      _log('interaction probe needs a signed-in session');
      return;
    }
    final answer = await _askTarget();
    final input = answer?.target;
    if (answer == null || input == null || input.isEmpty || !mounted) return;
    setState(() {
      _lines.clear();
      _running = true;
    });
    final uid = session.account.uid;
    final api = WeiboApi(
      createWeiboDio(
        jar: ref.read(cookieJarProvider),
        throttle: ref.read(requestThrottleProvider),
      ),
    );
    const web = 'https://weibo.com/ajax';

    Future<Map<String, Object?>?> get(
      String label,
      String path,
      Map<String, Object?> query,
    ) async {
      try {
        return await api.getJson('$web/$path', query: query);
      } on Exception catch (e) {
        _log('$label: $e');
        return null;
      }
    }

    // Posts without the error mapping, to see failures as they come.
    Future<Map<String, Object?>?> post(
      String label,
      String path,
      Map<String, Object?> body,
    ) async {
      try {
        return await api.postForm('$web/$path', body);
      } on WeiboApiException catch (e) {
        _log('$label: ${e.statusCode} ${_redact(e.message)}');
        return null;
      } on Exception catch (e) {
        _log('$label: $e');
        return null;
      }
    }

    void shape(String label, Map<String, Object?>? json) {
      if (json == null) return;
      _log('$label: ${describeShape({...json, 'data': null}, maxDepth: 2)}');
      if (json['data'] case final Object data) {
        _log('$label data: ${describeShape(data, maxDepth: 1)}');
      }
      for (final key in ['msg', 'message', 'error', 'error_type', 'url']) {
        if (json[key] case final Object v) {
          _log('  $key: ${v is String ? _redact(v) : describeShape(v)}');
        }
      }
    }

    // The target.
    String? id;
    if (input.startsWith('@')) {
      final profile = await get('profile', 'profile/info', {
        'screen_name': input.substring(1),
      });
      final user = readMap(readMap(profile?['data'])?['user']);
      final userId = readString(user?['idstr']);
      _log(
        'user found=${userId != null} '
        'statuses=${user?['statuses_count']} following=${user?['following']}',
      );
      // The user named a test account this one follows; anyone else is
      // probably a name mix-up, and gets no likes or comments.
      if (userId != null && user?['following'] != true) {
        _log('not followed; stopping');
      } else if (userId != null) {
        final page = await get('mymblog', 'statuses/mymblog', {
          'uid': userId,
          'page': 1,
          'feature': 0,
        });
        final first = _maps(readMap(page?['data'])?['list']).firstOrNull;
        id = readString(first?['idstr']);
      }
    } else {
      id = input;
    }
    if (id == null) {
      _log('no target');
      if (mounted) setState(() => _running = false);
      return;
    }

    Future<Map<String, Object?>?> show(String label) async {
      final s = await get(label, 'statuses/show', {'id': id});
      if (s != null) {
        _log(
          '$label: created=${s['created_at']} isTop=${s['isTop']} '
          'own=${readMap(s['user'])?['idstr'] == uid} '
          'liked=${s['attitudes_status']} likes=${s['attitudes_count']} '
          'comments=${s['comments_count']}',
        );
      }
      return s;
    }

    final status = await show('target');
    if (status == null) {
      if (mounted) setState(() => _running = false);
      return;
    }
    final mid = readString(status['idstr']) ?? id;
    final authorId = readString(readMap(status['user'])?['idstr']) ?? '';

    _log('== status likes');
    final wasLiked = readInt(status['attitudes_status']) == 1;
    for (final like in wasLiked ? [false, true] : [true, false]) {
      final path = like ? 'statuses/setLike' : 'statuses/cancelLike';
      final answer = await post(path, path, {'id': mid});
      shape(path, answer);
      _log('  result=${answer?['result']} attitude=${answer?['attitude']}');
      await show('after');
      await Future<void>.delayed(const Duration(seconds: 3));
      await show('3s later');
    }
    shape(
      'setLike bad id',
      await post('bad id', 'statuses/setLike', {'id': '1'}),
    );

    if (!answer.comments) {
      _log('== done');
      if (mounted) setState(() => _running = false);
      return;
    }

    _log('== comments');
    final comments = await get('comments', 'statuses/buildComments', {
      'is_reload': 1,
      'id': mid,
      'is_show_bulletin': 2,
      'is_mix': 0,
      'count': 20,
      'uid': authorId,
      'fetch_level': 0,
      'flow': 1,
      'locale': 'zh-CN',
    });
    _log('comments: ${_maps(comments?['data']).length}');

    final created = await post('create', 'comments/create', {
      'id': mid,
      'comment': 'maxbo 接口测试评论，稍后删除',
      'is_repost': 0,
      'comment_ori': 0,
      'is_comment': 0,
    });
    shape('create', created);
    final createdData = readMap(created?['data']);
    if (createdData != null) {
      _log(
        'create data keys: ${(createdData.keys.toList()..sort()).join(', ')}',
      );
    }
    final comment = Comment.tryFromJson(createdData);
    _log(
      'create parsed=${comment != null} '
      'own=${comment?.user?.id == uid} textLen=${comment?.text.length} '
      'liked=${comment?.liked} at=${comment?.createdAt != null} '
      'source=${comment?.source != null}',
    );

    if (comment != null) {
      final replied = await post('reply', 'comments/reply', {
        'id': mid,
        'cid': comment.id,
        'comment': 'maxbo 接口测试回复，稍后删除',
        'is_repost': 0,
        'comment_ori': 0,
        'is_comment': 0,
      });
      shape('reply', replied);
      final replyData = readMap(replied?['data']);
      if (replyData != null) {
        _log(
          'reply data keys: ${(replyData.keys.toList()..sort()).join(', ')}',
        );
        _log(
          'reply: reply_comment=${replyData['reply_comment'] != null} '
          'rootid=${'${replyData['rootid']}' == comment.id} '
          'textPrefix=${'${replyData['text']}'.startsWith('回复')}',
        );
      }
      final reply = Comment.tryFromJson(replyData);

      _log('== comment likes');
      for (final like in [true, false]) {
        final path = like ? 'statuses/updateLike' : 'statuses/destroyLike';
        shape(
          path,
          await post(path, path, {
            'object_id': comment.id,
            'object_type': 'comment',
          }),
        );
        final list = await get('comments', 'statuses/buildComments', {
          'is_reload': 1,
          'id': mid,
          'is_show_bulletin': 2,
          'is_mix': 0,
          'count': 20,
          'uid': authorId,
          'fetch_level': 0,
          'flow': 1,
          'locale': 'zh-CN',
        });
        final mine = _maps(list?['data'])
            .where((c) => readString(c['idstr']) == comment.id)
            .firstOrNull;
        _log(
          'listed=${mine != null} liked=${mine?['liked']} '
          'likes=${mine?['like_counts']} replies=${mine?['total_number']} '
          'preview=${_maps(mine?['comments']).length}',
        );
      }
      await show('after comment');

      _log('== delete');
      if (reply != null) {
        shape(
          'destroy reply',
          await post('destroy reply', 'statuses/destroyComment', {
            'cid': reply.id,
          }),
        );
      }
      shape(
        'destroy',
        await post('destroy', 'statuses/destroyComment', {'cid': comment.id}),
      );
      shape(
        'destroy again',
        await post('destroy again', 'statuses/destroyComment', {
          'cid': comment.id,
        }),
      );
      await show('after delete');
    }
    _log('== done');
    if (mounted) setState(() => _running = false);
  }

  /// Logs up to [pages] pages of the statuses of [user], stopping at an
  /// empty one: what each page holds and how pages relate.
  Future<void> _probeUserStatuses(
    Future<Map<String, Object?>?> Function(
      String label,
      String path,
      Map<String, Object?> query,
    )
    fetch,
    String user, {
    required int pages,
  }) async {
    final seen = <String>{};
    for (var n = 1; n <= pages; n++) {
      final label = 'p$n';
      final json = await fetch(label, 'statuses/mymblog', {
        'uid': user,
        'page': n,
        'feature': 0,
      });
      if (json == null) return;
      final data = readMap(json['data']);
      final list = _maps(data?['list']).toList();
      _log(
        '$label data: ${describeShape({...?data, 'list': null}, maxDepth: 2)}',
      );
      _log(
        '$label: list=${list.length} total=${data?['total']} '
        'since_id=${_patterns([data?['since_id']])} '
        'tips_visible=${data?['bottom_tips_visible']} '
        'tips_len=${'${data?['bottom_tips_text'] ?? ''}'.length} '
        'status_visible=${data?['status_visible']} '
        'overlap=${list.where((s) => !seen.add('${s['idstr']}')).length} '
        'order=${_order(list)}',
      );
      for (final (i, s) in list.indexed) {
        if (s['isTop'] == null && s['mblogtype'] == 0) continue;
        _log(
          '  #$i isTop=${s['isTop']} mblogtype=${s['mblogtype']} '
          'title=${describeShape(s['title'], maxDepth: 1)} '
          'own=${readMap(s['user'])?['idstr'] == user} '
          'retweet=${s['retweeted_status'] != null}',
        );
      }
      if (list.isEmpty) return;
    }
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
            tooltip: '打开页面',
            icon: const Icon(Icons.open_in_new),
            onPressed: _openRoute,
          ),
          IconButton(
            tooltip: '通知结构（只读，会清掉未读数）',
            icon: const Icon(Icons.notifications_none),
            onPressed: _running ? null : _probeNotifications,
          ),
          IconButton(
            tooltip: '互动结构（会真实点赞和评论）',
            icon: const Icon(Icons.thumb_up_outlined),
            onPressed: _running ? null : _probeInteractions,
          ),
          IconButton(
            tooltip: '广告字段（只读）',
            icon: const Icon(Icons.campaign_outlined),
            onPressed: _running ? null : _probeAds,
          ),
          IconButton(
            tooltip: '主页与热搜结构',
            icon: const Icon(Icons.person_search_outlined),
            onPressed: _running ? null : _probeProfile,
          ),
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

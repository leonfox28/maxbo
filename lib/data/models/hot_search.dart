import '../../core/util/json_read.dart';

/// Weibo's hot search list ("热搜榜"), as `side/hotSearch` sends it.
class HotSearch {
  const HotSearch({this.pinned = const [], this.items = const []});

  /// Entries pinned above the ranking.
  final List<HotSearchItem> pinned;

  /// The ranking, with the entries Weibo inserts into it (including paid
  /// ones) where Weibo puts them. Nothing is filtered out (PLAN §2).
  final List<HotSearchItem> items;

  factory HotSearch.fromJson(Map<String, Object?> data) {
    // Both are sent: `hotgovs` lists the pinned entries, `hotgov` holds one
    // (with a few more fields).
    final govs = readMaps(data['hotgovs']);
    final pinned = govs.isNotEmpty ? govs : [?readMap(data['hotgov'])];
    return HotSearch(
      pinned: [for (final p in pinned) ?HotSearchItem.tryFromJson(p)],
      items: [
        for (final i in readMaps(data['realtime']))
          ?HotSearchItem.tryFromJson(i),
      ],
    );
  }
}

class HotSearchItem {
  const HotSearchItem({
    required this.word,
    required this.query,
    this.rank,
    this.heat,
    this.badge,
    this.badgeColor,
    this.isAd = false,
  });

  /// The entry as shown, without the `#` of topics.
  final String word;

  /// What to search for: the word, or `#word#` for a topic.
  final String query;

  /// The position in the ranking, from 1; null for entries outside it.
  final int? rank;

  /// Weibo's heat index.
  final int? heat;

  /// Weibo's label, such as 热, 新, or 商 for a paid entry.
  final String? badge;

  /// The label colour as 0xAARRGGBB.
  final int? badgeColor;

  /// Marked `is_ad` by Weibo. This covers more than paid entries (a rumour
  /// rebuttal had it too), so it isn't shown as an ad label by itself.
  final bool isAd;

  static HotSearchItem? tryFromJson(Map<String, Object?> json) {
    final raw =
        readString(json['word']) ??
        readString(json['note']) ??
        readString(json['name']);
    if (raw == null) return null;
    final word = raw.length > 2 && raw.startsWith('#') && raw.endsWith('#')
        ? raw.substring(1, raw.length - 1)
        : raw;
    final isAd = readInt(json['is_ad']) == 1;
    // The short label is the one that says 商 on paid entries.
    final short = readString(json['small_icon_desc']);
    final badge = short ?? readString(json['icon_desc']);
    final color = short != null
        ? json['small_icon_desc_color']
        : json['icon_desc_color'];
    return HotSearchItem(
      word: word,
      query: readString(json['word_scheme']) ?? raw,
      rank: readInt(json['realpos']),
      heat: readInt(json['num']),
      // Never leave a promoted entry unmarked.
      badge: badge ?? (isAd ? '荐' : null),
      badgeColor: _color(color),
      isAd: isAd,
    );
  }

  /// Parses `#rrggbb`.
  static int? _color(Object? value) {
    final hex = readString(value);
    if (hex == null || !RegExp(r'^#[0-9a-fA-F]{6}$').hasMatch(hex)) return null;
    return 0xFF000000 | int.parse(hex.substring(1), radix: 16);
  }
}

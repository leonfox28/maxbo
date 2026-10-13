const _months = {
  'Jan': 1,
  'Feb': 2,
  'Mar': 3,
  'Apr': 4,
  'May': 5,
  'Jun': 6,
  'Jul': 7,
  'Aug': 8,
  'Sep': 9,
  'Oct': 10,
  'Nov': 11,
  'Dec': 12,
};

final _weiboDate = RegExp(
  r'^\w{3} (\w{3}) (\d{1,2}) (\d{2}):(\d{2}):(\d{2}) ([+-])(\d{2})(\d{2}) (\d{4})$',
);

/// Parses Weibo's `created_at`, e.g. `Mon Oct 12 13:17:46 +0800 2026`, into
/// a UTC time; null if it has another format.
DateTime? parseWeiboDate(String? value) {
  final m = value == null ? null : _weiboDate.firstMatch(value.trim());
  final month = m == null ? null : _months[m[1]];
  if (m == null || month == null) return null;
  final offset = Duration(hours: int.parse(m[7]!), minutes: int.parse(m[8]!));
  final local = DateTime.utc(
    int.parse(m[9]!),
    month,
    int.parse(m[2]!),
    int.parse(m[3]!),
    int.parse(m[4]!),
    int.parse(m[5]!),
  );
  return m[6] == '+' ? local.subtract(offset) : local.add(offset);
}

/// A post time the way Weibo shows it: relative when recent, a date when not.
String formatPostTime(DateTime time, {DateTime? now}) {
  final current = (now ?? DateTime.now()).toLocal();
  final t = time.toLocal();
  final diff = current.difference(t);
  if (diff.inMinutes < 1) return '刚刚';
  if (diff.inHours < 1) return '${diff.inMinutes}分钟前';
  final today = DateTime(current.year, current.month, current.day);
  if (!t.isBefore(today)) return '${diff.inHours}小时前';
  final hm = '${_two(t.hour)}:${_two(t.minute)}';
  if (!t.isBefore(today.subtract(const Duration(days: 1)))) return '昨天 $hm';
  if (t.year == current.year) return '${_two(t.month)}-${_two(t.day)} $hm';
  return '${t.year}-${_two(t.month)}-${_two(t.day)}';
}

/// A count in Chinese units, rounded down so it never overstates:
/// 9999, 1.2万, 3亿.
String formatCount(int count) {
  String scaled(int unit, String suffix) {
    final tenths = count * 10 ~/ unit;
    final whole = tenths ~/ 10;
    final fraction = tenths % 10;
    return '${fraction == 0 || whole >= 100 ? whole : '$whole.$fraction'}$suffix';
  }

  if (count < 10000) return '$count';
  if (count < 100000000) return scaled(10000, '万');
  return scaled(100000000, '亿');
}

/// Seconds as `m:ss`, or `h:mm:ss` from an hour up.
String formatDuration(int seconds) {
  final h = seconds ~/ 3600;
  final m = seconds % 3600 ~/ 60;
  final s = _two(seconds % 60);
  return h > 0 ? '$h:${_two(m)}:$s' : '$m:$s';
}

String _two(int n) => n.toString().padLeft(2, '0');

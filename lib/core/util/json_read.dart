// Lenient readers for Weibo's JSON, where fields come and go and numbers are
// sometimes sent as strings.

/// A non-empty string, or an integer rendered as one; ids are always kept as
/// strings.
String? readString(Object? value) => switch (value) {
  String() when value.isNotEmpty => value,
  int() => '$value',
  _ => null,
};

int? readInt(Object? value) => switch (value) {
  int() => value,
  double() => value.toInt(),
  String() => int.tryParse(value),
  _ => null,
};

Map<String, Object?>? readMap(Object? value) =>
    value is Map<String, Object?> ? value : null;

/// The maps in [value], skipping anything else; empty if it isn't a list.
List<Map<String, Object?>> readMaps(Object? value) =>
    value is List ? value.whereType<Map<String, Object?>>().toList() : const [];

/// An absolute https URL; Weibo often sends protocol-relative ones.
String? readUrl(Object? value) {
  final url = readString(value);
  if (url == null) return null;
  if (url.startsWith('//')) return 'https:$url';
  return url.startsWith('http') ? url : null;
}

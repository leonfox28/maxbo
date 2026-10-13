/// Describes the structure of decoded JSON without any of its values, e.g.
/// `{ok: int, data: {uid: str, tags: [3× str]}}`.
///
/// Safe to log: it reveals which fields exist, never what they contain.
String describeShape(Object? json, {int maxDepth = 4}) {
  if (maxDepth < 0) return '…';
  return switch (json) {
    null => 'null',
    bool() => 'bool',
    int() => 'int',
    double() => 'double',
    String() => 'str',
    List() when json.isEmpty => '[]',
    List() =>
      '[${json.length}× ${describeShape(json.first, maxDepth: maxDepth - 1)}]',
    Map() =>
      '{${json.entries.map((e) => '${e.key}: ${describeShape(e.value, maxDepth: maxDepth - 1)}').join(', ')}}',
    _ => json.runtimeType.toString(),
  };
}

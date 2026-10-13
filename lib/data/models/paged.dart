/// One page of a list that continues from a cursor.
class Paged<T> {
  const Paged(this.items, {this.nextCursor, this.total});

  final List<T> items;

  /// Fetches the page after this one; null at the end.
  final String? nextCursor;

  /// How many items the whole list has, when Weibo says.
  final int? total;
}

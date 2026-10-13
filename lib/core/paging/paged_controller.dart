import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/models/paged.dart';
import '../net/weibo_exception.dart';

class PagedState<T> {
  const PagedState({
    this.items = const [],
    this.nextCursor,
    this.total,
    this.loadingMore = false,
    this.loadMoreError,
  });

  final List<T> items;

  /// Where the next page starts; null once the end is reached.
  final String? nextCursor;

  /// The size of the whole list, when Weibo says.
  final int? total;
  final bool loadingMore;

  /// Why the last attempt to load more failed; cleared by the next attempt.
  final WeiboException? loadMoreError;

  bool get hasMore => nextCursor != null;

  /// This list with [items] in place of its own, [total] changing by
  /// [totalChange].
  PagedState<T> withItems(List<T> items, {int totalChange = 0}) => PagedState(
    items: items,
    nextCursor: nextCursor,
    total: total == null ? null : max(0, total! + totalChange),
    loadingMore: loadingMore,
    loadMoreError: loadMoreError,
  );
}

/// A list loaded a page at a time: the first page when first watched, the
/// next on [loadMore] as the user scrolls. Nothing loads on its own (PLAN §2).
abstract class PagedController<T> extends AsyncNotifier<PagedState<T>> {
  /// Fetches the page at [cursor], or the first page when null.
  Future<Paged<T>> fetchPage(String? cursor);

  /// Identifies [item], to drop repeats when pages overlap.
  String idOf(T item);

  @override
  Future<PagedState<T>> build() async {
    final page = await fetchPage(null);
    return PagedState(
      items: page.items,
      nextCursor: page.nextCursor,
      total: page.total,
    );
  }

  /// Appends the next page. Failures are kept in
  /// [PagedState.loadMoreError] rather than thrown.
  Future<void> loadMore() async {
    final current = state.value;
    if (current == null ||
        state.isLoading ||
        current.loadingMore ||
        !current.hasMore) {
      return;
    }
    final loading = PagedState<T>(
      items: current.items,
      nextCursor: current.nextCursor,
      total: current.total,
      loadingMore: true,
    );
    state = AsyncData(loading);

    // `ref` follows rebuilds; the captured one tells if this build is gone.
    final buildRef = ref;
    Paged<T>? page;
    WeiboException? error;
    try {
      page = await fetchPage(current.nextCursor);
    } on WeiboException catch (e) {
      error = e;
    }
    // Drop the result if a refresh replaced the list meanwhile; otherwise
    // add it to the list as it is now, which the user may have changed.
    final latest = state.value;
    if (!buildRef.mounted || latest == null) return;
    if (page == null) {
      state = AsyncData(
        PagedState(
          items: latest.items,
          nextCursor: latest.nextCursor,
          total: latest.total,
          loadMoreError: error,
        ),
      );
      return;
    }
    final seen = {for (final item in latest.items) idOf(item)};
    state = AsyncData(
      PagedState(
        items: [
          ...latest.items,
          ...page.items.where((item) => seen.add(idOf(item))),
        ],
        nextCursor: page.nextCursor,
        total: page.total ?? latest.total,
      ),
    );
  }

  /// Puts [item], which the user just added, atop the list.
  void prepend(T item) => _edit((list) {
    final id = idOf(item);
    return list.withItems([
      item,
      ...list.items.where((i) => idOf(i) != id),
    ], totalChange: 1);
  });

  /// Takes the item [id], which the user just deleted, out of the list.
  void remove(String id) => _edit((list) {
    final items = list.items.where((i) => idOf(i) != id).toList();
    if (items.length == list.items.length) return list;
    return list.withItems(items, totalChange: -1);
  });

  /// Replaces the item [id] with what [change] makes of it.
  void replace(String id, T Function(T item) change) => _edit(
    (list) => list.withItems([
      for (final i in list.items) idOf(i) == id ? change(i) : i,
    ]),
  );

  // Lists still loading their first page are left alone: that page will be
  // current anyway.
  void _edit(PagedState<T> Function(PagedState<T> list) change) {
    final list = state.value;
    if (list == null || state.isLoading) return;
    state = AsyncData(change(list));
  }
}

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
    PagedState<T> next;
    try {
      final page = await fetchPage(current.nextCursor);
      final seen = {for (final item in current.items) idOf(item)};
      next = PagedState(
        items: [
          ...current.items,
          ...page.items.where((item) => seen.add(idOf(item))),
        ],
        nextCursor: page.nextCursor,
        total: page.total ?? current.total,
      );
    } on WeiboException catch (e) {
      next = PagedState(
        items: current.items,
        nextCursor: current.nextCursor,
        total: current.total,
        loadMoreError: e,
      );
    }
    // Drop the result if a refresh replaced the list meanwhile.
    if (buildRef.mounted && identical(state.value, loading)) {
      state = AsyncData(next);
    }
  }
}

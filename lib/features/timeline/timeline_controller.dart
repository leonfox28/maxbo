import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/feed_page.dart';
import '../../data/models/status.dart';

final timelineProvider =
    AsyncNotifierProvider<TimelineController, TimelineState>(
      TimelineController.new,
    );

class TimelineState {
  const TimelineState({
    this.statuses = const [],
    this.nextCursor,
    this.loadingMore = false,
    this.loadMoreError,
  });

  final List<Status> statuses;

  /// Where the next, older page starts; null once the end is reached.
  final String? nextCursor;
  final bool loadingMore;

  /// Why the last attempt to load more failed; cleared by the next attempt.
  final WeiboException? loadMoreError;

  bool get hasMore => nextCursor != null;
}

/// The signed-in user's "最新微博" timeline.
///
/// Every request follows a user action: opening the tab, pulling to refresh,
/// or scrolling to the end. Nothing polls or prefetches (PLAN §2).
class TimelineController extends AsyncNotifier<TimelineState> {
  @override
  Future<TimelineState> build() async {
    final uid = ref.watch(
      sessionProvider.select((s) => s is SignedIn ? s.account.uid : null),
    );
    if (uid == null) return const TimelineState();
    return _firstPage(uid);
  }

  Future<TimelineState> _firstPage(String uid) async {
    final page = await ref.read(weiboApiProvider).fetchHomeTimeline(uid: uid);
    return TimelineState(statuses: page.statuses, nextCursor: page.nextCursor);
  }

  String? get _uid => switch (ref.read(sessionProvider)) {
    SignedIn(:final account) => account.uid,
    SignedOut() => null,
  };

  /// Replaces the timeline with its newest page.
  ///
  /// On failure the current statuses stay and the [WeiboException] is
  /// rethrown for the caller to report.
  Future<void> refresh() async {
    final uid = _uid;
    if (uid == null) return;
    // `ref` follows rebuilds; the captured one tells if this build is gone.
    final buildRef = ref;
    final next = await _firstPage(uid);
    if (buildRef.mounted && _uid == uid) {
      _refreshes++;
      state = AsyncData(next);
    }
  }

  /// Counts completed refreshes, so a page loaded meanwhile is dropped.
  int _refreshes = 0;

  /// Appends the next page. Failures are kept in
  /// [TimelineState.loadMoreError] rather than thrown.
  Future<void> loadMore() async {
    final current = state.value;
    final uid = _uid;
    if (uid == null ||
        current == null ||
        state.isLoading ||
        current.loadingMore ||
        !current.hasMore) {
      return;
    }
    final loading = TimelineState(
      statuses: current.statuses,
      nextCursor: current.nextCursor,
      loadingMore: true,
    );
    state = AsyncData(loading);

    final buildRef = ref;
    final refreshes = _refreshes;
    FeedPage? page;
    WeiboException? error;
    try {
      page = await ref
          .read(weiboApiProvider)
          .fetchHomeTimeline(uid: uid, maxId: current.nextCursor);
    } on WeiboException catch (e) {
      error = e;
    }
    // Drop the result if a refresh or a new session replaced the timeline.
    // A session change rebuilds this provider only later, so check it too.
    // Otherwise add to the timeline as it is now, which the user may have
    // changed by posting or deleting.
    final latest = state.value;
    if (!buildRef.mounted ||
        _uid != uid ||
        refreshes != _refreshes ||
        latest == null) {
      return;
    }
    if (page == null) {
      state = AsyncData(
        TimelineState(
          statuses: latest.statuses,
          nextCursor: latest.nextCursor,
          loadMoreError: error,
        ),
      );
      return;
    }
    // Pages can overlap when new posts arrive in between.
    final seen = {for (final s in latest.statuses) s.id};
    state = AsyncData(
      TimelineState(
        statuses: [
          ...latest.statuses,
          ...page.statuses.where((s) => seen.add(s.id)),
        ],
        nextCursor: page.nextCursor,
      ),
    );
  }

  /// Puts [status], which the user just posted, atop the timeline, if it
  /// is loaded.
  void prepend(Status status) => _edit(
    (statuses) => [status, ...statuses.where((s) => s.id != status.id)],
  );

  /// Takes status [id], which the user just deleted, out of the timeline.
  void remove(String id) =>
      _edit((statuses) => statuses.where((s) => s.id != id).toList());

  // A timeline still loading will show the change anyway.
  void _edit(List<Status> Function(List<Status> statuses) change) {
    final current = state.value;
    if (current == null || state.isLoading) return;
    state = AsyncData(
      TimelineState(
        statuses: change(current.statuses),
        nextCursor: current.nextCursor,
        loadingMore: current.loadingMore,
        loadMoreError: current.loadMoreError,
      ),
    );
  }
}

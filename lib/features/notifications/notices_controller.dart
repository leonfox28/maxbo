import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/paging/paged_controller.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/dev_log.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/notice.dart';
import '../../data/models/paged.dart';
import '../../data/models/status.dart';

/// How soon after one check returning to the app or refreshing the home
/// timeline checks again.
const unreadRecheckInterval = Duration(minutes: 1);

final unreadNoticesProvider =
    NotifierProvider<UnreadNoticesController, UnreadNotices>(
      UnreadNoticesController.new,
    );

/// Which kinds of notifications are unread, which the tab bar and the
/// notification page's tabs mark.
///
/// Checked only when the user signs in, opens the app or refreshes the home
/// timeline ([check]), never polled (PLAN §2). A kind stays marked until the user opens its tab
/// ([markRead]); a check only adds marks.
class UnreadNoticesController extends Notifier<UnreadNotices> {
  DateTime? _checkedAt;

  /// Kinds marked read while a check was out, whose result predates that.
  final _readDuringCheck = <NoticeKind>{};
  var _checking = false;

  @override
  UnreadNotices build() {
    ref.watch(
      sessionProvider.select((s) => s is SignedIn ? s.account.uid : null),
    );
    _checkedAt = null;
    return const UnreadNotices();
  }

  /// Asks Weibo what is unread, unless it was asked less than [minInterval]
  /// ago. Failures leave things as they were: the mark is a hint only.
  Future<void> check({Duration minInterval = Duration.zero}) async {
    if (ref.read(sessionProvider) is! SignedIn || _checking) return;
    final now = DateTime.now();
    final last = _checkedAt;
    if (last != null && now.difference(last) < minInterval) return;
    _checkedAt = now;
    _checking = true;
    _readDuringCheck.clear();
    // `ref` follows rebuilds; the captured one tells if this build is gone.
    final buildRef = ref;
    try {
      final unread = await ref.read(weiboApiProvider).fetchUnreadNotices();
      if (!buildRef.mounted) return;
      // Loading a list marks it read on Weibo's side, so a tab opened since
      // the check went out is up to date.
      state = UnreadNotices(
        {...state.kinds, ...unread.kinds}..removeAll(_readDuringCheck),
      );
    } on WeiboException catch (e) {
      devLog('unread notices: $e');
    } finally {
      _checking = false;
    }
  }

  /// The user opened the tab of [kind].
  void markRead(NoticeKind kind) {
    if (_checking) _readDuringCheck.add(kind);
    if (state.has(kind)) state = state.without(kind);
  }
}

final commentNoticesProvider = AsyncNotifierProvider.autoDispose
    .family<CommentNoticesController, PagedState<CommentNotice>, NoticeKind>(
      CommentNoticesController.new,
    );

final mentionStatusesProvider =
    AsyncNotifierProvider.autoDispose<
      MentionStatusesController,
      PagedState<Status>
    >(MentionStatusesController.new);

final likeNoticesProvider =
    AsyncNotifierProvider.autoDispose<
      LikeNoticesController,
      PagedState<LikeNotice>
    >(LikeNoticesController.new);

/// [NoticeKind.comments] or [NoticeKind.mentionComments].
class CommentNoticesController extends PagedController<CommentNotice> {
  CommentNoticesController(this.kind)
    : assert(kind == NoticeKind.comments || kind == NoticeKind.mentionComments);

  final NoticeKind kind;

  @override
  Future<Paged<CommentNotice>> fetchPage(String? cursor) {
    final api = ref.read(weiboApiProvider);
    return kind == NoticeKind.comments
        ? api.fetchCommentNotices(maxId: cursor)
        : api.fetchMentionComments(maxId: cursor);
  }

  @override
  String idOf(CommentNotice item) => item.id;
}

class MentionStatusesController extends PagedController<Status> {
  @override
  Future<Paged<Status>> fetchPage(String? cursor) =>
      ref.read(weiboApiProvider).fetchMentionStatuses(maxId: cursor);

  @override
  String idOf(Status item) => item.id;
}

class LikeNoticesController extends PagedController<LikeNotice> {
  @override
  Future<Paged<LikeNotice>> fetchPage(String? cursor) =>
      ref.read(weiboApiProvider).fetchLikeNotices(maxId: cursor);

  @override
  String idOf(LikeNotice item) => item.id;
}

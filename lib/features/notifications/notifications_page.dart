import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart';
import 'package:go_router/go_router.dart';

import '../../core/media/weibo_image.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/paging/paged_controller.dart';
import '../../core/session/session_controller.dart';
import '../../core/settings/app_settings.dart';
import '../../core/theme.dart';
import '../../core/util/format.dart';
import '../../data/models/comment.dart';
import '../../data/models/notice.dart';
import '../../data/models/status.dart';
import '../../widgets/load_state.dart';
import '../../widgets/rich_text/status_text_parser.dart';
import '../../widgets/status_card/status_card.dart';
import '../../widgets/tab_reselect.dart';
import '../../widgets/user_avatar.dart';
import '../auth/login_prompt.dart';
import '../comments/comment_tile.dart';
import '../status/status_actions.dart';
import 'notices_controller.dart';

const _tabs = [
  (kind: NoticeKind.comments, label: '评论'),
  (kind: NoticeKind.mentionStatuses, label: '@我的微博'),
  (kind: NoticeKind.mentionComments, label: '@我的评论'),
  (kind: NoticeKind.likes, label: '赞'),
];

/// The notifications tab: comments, mentions and likes, one list each.
///
/// A list loads when its tab is first shown, which marks its kind read on
/// Weibo's side too. Shown again while marked unread, it reloads.
class NotificationsPage extends ConsumerWidget {
  const NotificationsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // No app bar: the tab bar names the tab.
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: switch (ref.watch(sessionProvider)) {
          SignedOut(:final expired) => LoginPrompt(expired: expired),
          SignedIn() => const _Notices(),
        },
      ),
    );
  }
}

class _Notices extends ConsumerStatefulWidget {
  const _Notices();

  @override
  ConsumerState<_Notices> createState() => _NoticesState();
}

class _NoticesState extends ConsumerState<_Notices>
    with SingleTickerProviderStateMixin {
  late final _tabController = TabController(length: _tabs.length, vsync: this)
    ..addListener(_reloadShownIfUnread);

  // Each list scrolls on its own, and only the shown one answers a tap on
  // the open home tab.
  final _scrollControllers = [for (final _ in _tabs) ScrollController()];
  final _taps = [for (final _ in _tabs) _Taps()];
  Listenable? _homeTaps;
  var _shown = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final taps = TabReselect.maybeOf(context);
    if (taps != _homeTaps) {
      _homeTaps?.removeListener(_onHomeTap);
      _homeTaps = taps?..addListener(_onHomeTap);
    }
    // The home tabs mute the tickers of all but the open one.
    final shown = TickerMode.valuesOf(context).enabled;
    if (shown != _shown) {
      _shown = shown;
      _reloadShownIfUnread();
    }
  }

  @override
  void dispose() {
    _homeTaps?.removeListener(_onHomeTap);
    _tabController.dispose();
    for (final c in _scrollControllers) {
      c.dispose();
    }
    for (final t in _taps) {
      t.dispose();
    }
    super.dispose();
  }

  void _onHomeTap() => _taps[_tabController.index].tap();

  /// Reloads the list in view if it is marked unread: it was loaded before
  /// whatever is new. Lists not loaded yet will be once built.
  void _reloadShownIfUnread() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_shown) return;
      final kind = _tabs[_tabController.index].kind;
      if (!ref.read(unreadNoticesProvider).has(kind)) return;
      final provider = _listProvider(kind);
      if (ref.exists(provider) && !ref.read(provider).isLoading) {
        ref.invalidate(provider);
      }
    });
  }

  static ProviderBase<AsyncValue<Object?>> _listProvider(NoticeKind kind) =>
      switch (kind) {
        NoticeKind.comments ||
        NoticeKind.mentionComments => commentNoticesProvider(kind),
        NoticeKind.mentionStatuses => mentionStatusesProvider,
        NoticeKind.likes => likeNoticesProvider,
      };

  @override
  Widget build(BuildContext context) {
    ref.listen(unreadNoticesProvider, (_, _) => _reloadShownIfUnread());
    final unread = ref.watch(unreadNoticesProvider);
    return Column(
      children: [
        TabBar(
          controller: _tabController,
          tabs: [
            for (final tab in _tabs)
              Tab(child: _TabLabel(tab.label, unread: unread.has(tab.kind))),
          ],
        ),
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: [
              for (final (i, tab) in _tabs.indexed)
                TabReselect(
                  taps: _taps[i],
                  child: PrimaryScrollController(
                    controller: _scrollControllers[i],
                    child: _NoticeList(tab.kind),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Taps extends ChangeNotifier {
  void tap() => notifyListeners();
}

/// A tab's name, with a dot while it has something unread.
class _TabLabel extends StatelessWidget {
  const _TabLabel(this.label, {required this.unread});

  final String label;
  final bool unread;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: unread ? '$label，有未读' : label,
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label),
          if (unread)
            Container(
              width: 6,
              height: 6,
              margin: const EdgeInsets.only(left: 3, bottom: 8),
              decoration: const BoxDecoration(
                color: unreadColor,
                shape: BoxShape.circle,
              ),
            ),
        ],
      ),
    );
  }
}

/// One kind's list, kept while the user looks at the other tabs.
class _NoticeList extends ConsumerStatefulWidget {
  const _NoticeList(this.kind);

  final NoticeKind kind;

  @override
  ConsumerState<_NoticeList> createState() => _NoticeListState();
}

class _NoticeListState extends ConsumerState<_NoticeList>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final actions = statusCardActions(context);
    return switch (widget.kind) {
      NoticeKind.comments || NoticeKind.mentionComments => _list(
        commentNoticesProvider(widget.kind),
        emptyText: widget.kind == NoticeKind.comments ? '还没有收到评论' : '还没有评论提到你',
        itemBuilder: (context, notice) =>
            _CommentNoticeTile(notice, actions: actions),
      ),
      NoticeKind.mentionStatuses => _list(
        mentionStatusesProvider,
        emptyText: '还没有微博提到你',
        dividerIndent: 0,
        itemBuilder: (context, status) => StatusCard(status, actions: actions),
      ),
      NoticeKind.likes => _list(
        likeNoticesProvider,
        emptyText: '还没有收到赞',
        itemBuilder: (context, notice) =>
            _LikeNoticeTile(notice, actions: actions),
      ),
    };
  }

  Widget _list<T>(
    AsyncNotifierProvider<PagedController<T>, PagedState<T>> provider, {
    required String emptyText,
    required Widget Function(BuildContext context, T item) itemBuilder,
    double dividerIndent = 58,
  }) {
    final value = ref.watch(provider);
    Future<void> refresh() async {
      try {
        ref.invalidate(provider);
        await ref.read(provider.future);
      } on WeiboException catch (e) {
        if (mounted) showErrorSnackBar(context, e);
      }
    }

    return LoadMoreTrigger(
      onNearEnd: value.value?.loadMoreError == null
          ? ref.read(provider.notifier).loadMore
          : null,
      child: TabRefreshIndicator(
        onRefresh: refresh,
        child: CustomScrollView(
          primary: true,
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            // Clear of the floating tab bar at the end.
            SliverSafeArea(
              top: false,
              sliver: PagedSliverList<T>(
                value: value,
                itemBuilder: itemBuilder,
                emptyText: emptyText,
                dividerIndent: dividerIndent,
                onRetry: () => ref.invalidate(provider),
                onLoadMore: () => ref.read(provider.notifier).loadMore(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A comment to the user, above the comment it answers (if any) and the post
/// it is on. Tapping it opens the post.
class _CommentNoticeTile extends StatelessWidget {
  const _CommentNoticeTile(this.notice, {required this.actions});

  final CommentNotice notice;
  final StatusCardActions actions;

  @override
  Widget build(BuildContext context) {
    final status = notice.status;
    final replyTo = notice.replyTo;
    final onOpen = actions.onOpen;
    final open = status == null || status.user == null || onOpen == null
        ? null
        : () => onOpen(status);
    return CommentTile(
      notice.comment,
      actions: actions,
      onTap: open,
      below: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: 6,
        children: [
          if (replyTo != null) _CommentQuote(replyTo),
          if (status != null) _StatusQuote(status, onTap: open),
        ],
      ),
    );
  }
}

/// Who liked what, and when. Tapping it opens the post.
class _LikeNoticeTile extends StatelessWidget {
  const _LikeNoticeTile(this.notice, {required this.actions});

  final LikeNotice notice;
  final StatusCardActions actions;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final user = notice.user;
    final created = notice.createdAt;
    final status = notice.status;
    final comment = notice.comment;
    VoidCallback? open;
    if (status != null && status.user != null) {
      // A cut-down post would show without its pictures and counts until the
      // detail page has loaded it.
      open = notice.statusComplete
          ? () => actions.onOpen?.call(status)
          : () => context.push('/status/${status.id}');
    }
    return InkWell(
      onTap: open,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            UserAvatar(user, size: 32, onTap: actions.onUser),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text.rich(
                    TextSpan(
                      children: [
                        TextSpan(
                          text: user?.screenName ?? '微博用户',
                          style: TextStyle(
                            color: muted,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                        TextSpan(text: comment != null ? ' 赞了你的评论' : ' 赞了你的微博'),
                      ],
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodyMedium,
                  ),
                  if (created != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      formatPostTime(created),
                      style: theme.textTheme.bodySmall?.copyWith(color: muted),
                    ),
                  ],
                  const SizedBox(height: 8),
                  if (comment != null) ...[
                    _CommentQuote(comment),
                    const SizedBox(height: 6),
                  ],
                  if (status != null) _StatusQuote(status, onTap: open),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A comment cited in a notification: its author and the start of its text.
class _CommentQuote extends StatelessWidget {
  const _CommentQuote(this.comment);

  final Comment comment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    return Container(
      padding: const EdgeInsets.only(left: 8),
      decoration: BoxDecoration(
        border: Border(
          left: BorderSide(color: theme.colorScheme.outlineVariant, width: 2),
        ),
      ),
      child: Text.rich(
        TextSpan(
          children: [
            if (comment.user case final user?)
              TextSpan(
                text: '${user.screenName}：',
                style: const TextStyle(fontWeight: FontWeight.w500),
              ),
            TextSpan(text: plainStatusText(comment.text)),
          ],
        ),
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.bodySmall?.copyWith(color: muted),
      ),
    );
  }
}

/// The post a notification is about, as a small card: a picture, its author
/// and the start of its text.
class _StatusQuote extends ConsumerWidget {
  const _StatusQuote(this.status, {this.onTap});

  final Status status;
  final VoidCallback? onTap;

  static const _thumb = 48.0;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final user = status.user;
    final String? thumb;
    if (status.pics.firstOrNull case final pic?) {
      final pixels = ref
          .watch(settingsProvider.select((s) => s.imageQuality))
          .listPixels(_thumb, MediaQuery.devicePixelRatioOf(context));
      thumb = pic.variantFor(pixels).url;
    } else {
      thumb = status.video?.coverUrl;
    }
    return Material(
      color: colors.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(6),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Row(
            children: [
              if (thumb != null) ...[
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: WeiboImage(thumb, width: _thumb, height: _thumb),
                ),
                const SizedBox(width: 8),
              ],
              Expanded(
                // Deleted or hidden posts come without an author.
                child: user == null
                    ? Text(
                        '这条微博已被删除或不可见',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: colors.onSurfaceVariant,
                        ),
                      )
                    : Text.rich(
                        TextSpan(
                          children: [
                            TextSpan(
                              text: '@${user.screenName}：',
                              style: const TextStyle(
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                            TextSpan(text: plainStatusText(status.text)),
                          ],
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: colors.onSurfaceVariant,
                        ),
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

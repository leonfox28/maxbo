import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/format.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/comment.dart';
import '../../data/models/status.dart';
import '../../widgets/adaptive_layout.dart';
import '../../widgets/bars_on_scroll.dart';
import '../../widgets/load_state.dart';
import '../../widgets/rich_text/status_text.dart';
import '../../widgets/status_card/status_card.dart';
import '../../widgets/user_avatar.dart';
import '../comments/comment_replies_page.dart';
import '../comments/comment_tile.dart';
import '../comments/comments_controller.dart';
import '../interactions/comment_composer.dart';
import '../interactions/delete_status.dart';
import '../interactions/likes.dart';
import '../interactions/repost.dart';
import 'status_actions.dart';
import 'status_detail_providers.dart';

/// A status with its full text, and its comments and reposts in tabs. On an
/// expanded window the lists sit beside the status rather than under it,
/// each scrolling on its own, so a long status or one with many pictures
/// doesn't push the comments out of sight.
///
/// Opening the page loads the status and its first page of comments; the
/// reposts load only when their tab is opened (PLAN §2).
class StatusDetailPage extends ConsumerStatefulWidget {
  const StatusDetailPage({super.key, required this.id, this.initial});

  final String id;

  /// The status as the previous page showed it, shown until the full one
  /// arrives.
  final Status? initial;

  @override
  ConsumerState<StatusDetailPage> createState() => _StatusDetailPageState();
}

enum _Tab { reposts, comments }

const _title = '微博正文';

class _StatusDetailPageState extends ConsumerState<StatusDetailPage> {
  var _tab = _Tab.comments;
  var _order = CommentOrder.hot;

  // Lists opened so far stay loaded while the page is open, so going back to
  // one doesn't fetch it again.
  final _openedOrders = {CommentOrder.hot};
  var _repostsOpened = false;

  /// Comments the user added here, less those they deleted, and their
  /// reposts, since the status they were counted on loaded: newer data
  /// already has them.
  ({Status on, int comments, int reposts})? _added;
  final _drafts = <String, CommentDraft>{};
  final _bars = BarsOnScroll();
  final _deleting = <String>{};

  void _showTab(_Tab tab) => setState(() {
    _tab = tab;
    if (tab == _Tab.reposts) _repostsOpened = true;
  });

  void _showOrder(CommentOrder order) => setState(() {
    _order = order;
    _openedOrders.add(order);
  });

  CommentsQuery? _commentsQuery(Status status, CommentOrder order) {
    final authorId = status.user?.id;
    if (authorId == null) return null;
    return (statusId: status.id, authorId: authorId, order: order);
  }

  ({int comments, int reposts}) _addedTo(Status status) {
    final added = _added;
    return added != null && identical(added.on, status)
        ? (comments: added.comments, reposts: added.reposts)
        : (comments: 0, reposts: 0);
  }

  int _commentsCount(Status status) =>
      max(0, status.commentsCount + _addedTo(status).comments);

  int _repostsCount(Status status) =>
      status.repostsCount + _addedTo(status).reposts;

  void _count(Status status, {int comments = 0, int reposts = 0}) {
    final added = _addedTo(status);
    setState(
      () => _added = (
        on: status,
        comments: added.comments + comments,
        reposts: added.reposts + reposts,
      ),
    );
  }

  void _editComments(
    Status status,
    void Function(CommentsController list) edit,
  ) {
    final authorId = status.user?.id;
    if (authorId == null) return;
    editOpenCommentLists(
      ref,
      statusId: status.id,
      authorId: authorId,
      edit: edit,
    );
  }

  Future<void> _comment(Status status) async {
    final sent = await showCommentComposer(
      context,
      statusId: status.id,
      hint: '写评论…',
      drafts: _drafts,
      draftKey: 'status',
      send: (text, pid) async {
        final comment = await ref
            .read(weiboApiProvider)
            .postComment(statusId: status.id, text: text, pid: pid);
        if (!mounted) return;
        _count(status, comments: 1);
        setState(() => _tab = _Tab.comments);
        if (comment != null) _editComments(status, (l) => l.prepend(comment));
      },
    );
    if (sent && mounted) _showSnackBar('评论成功');
  }

  Future<void> _reply(Status status, Comment target) async {
    final name = target.user?.screenName;
    final sent = await showCommentComposer(
      context,
      statusId: status.id,
      hint: name == null ? '回复评论…' : '回复 @$name',
      drafts: _drafts,
      draftKey: target.id,
      send: (text, pid) async {
        final reply = await ref
            .read(weiboApiProvider)
            .postComment(
              statusId: status.id,
              text: text,
              replyTo: target.id,
              pid: pid,
            );
        if (!mounted) return;
        // Weibo counts replies among a status's comments.
        _count(status, comments: 1);
        _editComments(
          status,
          (list) => list.replace(target.id, (c) => withReply(c, reply)),
        );
      },
    );
    if (sent && mounted) _showSnackBar('回复成功');
  }

  // The list of reposts, if open, gets the new one from [repostStatus].
  Future<void> _repost(Status status) => repostStatus(
    context,
    status,
    onReposted: () {
      if (mounted) _count(status, reposts: 1);
    },
  );

  Future<void> _delete(Status status, Comment comment) async {
    if (!await confirmDelete(context) || !mounted) return;
    if (!_deleting.add(comment.id)) return;
    try {
      await ref.read(weiboApiProvider).deleteComment(comment.id);
      if (!mounted) return;
      _count(status, comments: -1);
      _editComments(status, (list) => list.remove(comment.id));
      _showSnackBar('已删除');
    } on WeiboException catch (e) {
      if (mounted) showErrorSnackBar(context, e);
    } finally {
      _deleting.remove(comment.id);
    }
  }

  void _showSnackBar(String message) => ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));

  Future<void> _refresh(Status status) async {
    final query = _commentsQuery(status, _order);
    try {
      await Future.wait([
        ref.refresh(statusDetailProvider(widget.id).future),
        if (_tab == _Tab.reposts)
          ref.refresh(repostsProvider(status.id).future)
        else if (query != null)
          ref.refresh(commentsProvider(query).future),
      ]);
    } on WeiboException catch (e) {
      if (mounted) showErrorSnackBar(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(statusDetailProvider(widget.id));
    final status = detail.value ?? widget.initial;
    final split = WindowSize.of(context) == WindowSize.expanded;
    return Scaffold(
      // Once there is a status, its bars give way while the user reads down
      // it, as the home tab bar does.
      appBar: status == null ? AppBar(title: const Text(_title)) : null,
      extendBody: true,
      bottomNavigationBar: status == null || status.user == null
          ? null
          : AnimatedSlide(
              offset: _bars.visible ? Offset.zero : const Offset(0, 1.5),
              duration: BarsOnScroll.duration,
              curve: BarsOnScroll.curve,
              // Under the lists it writes to.
              child: split
                  ? ReadingPanes(
                      end: _bottomBar(status),
                      divider: false,
                      crossAxisAlignment: CrossAxisAlignment.end,
                    )
                  : _bottomBar(status),
            ),
      body: status != null
          ? SafeArea(
              bottom: false,
              child: Column(
                children: [
                  HidingAppBar(
                    visible: _bars.visible,
                    title: const Text(_title),
                  ),
                  Expanded(
                    child: NotificationListener<ScrollUpdateNotification>(
                      onNotification: (n) {
                        if (_bars.onScroll(n)) setState(() {});
                        return false;
                      },
                      // Under the scaffold, which pads for the comment bar.
                      child: Builder(
                        builder: (context) =>
                            _content(context, status, detail, split: split),
                      ),
                    ),
                  ),
                ],
              ),
            )
          : detail.hasError
          ? ErrorRetry(
              error: detail.error,
              onRetry: () => ref.invalidate(statusDetailProvider(widget.id)),
            )
          : const Center(child: CircularProgressIndicator()),
    );
  }

  Widget _bottomBar(Status status) {
    final colors = Theme.of(context).colorScheme;
    // No ink, as on a card's counts.
    ButtonStyle plain(Color color) => TextButton.styleFrom(
      foregroundColor: color,
      overlayColor: Colors.transparent,
    );
    final muted = plain(colors.onSurfaceVariant);
    return CommentBar(
      hint: '写评论…',
      onTap: () => _comment(status),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          // The count is on the tab above.
          TextButton.icon(
            onPressed: () => _repost(status),
            style: muted,
            icon: const Icon(Icons.repeat),
            label: const Text('转发'),
          ),
          LikeBuilder(
            target: (kind: LikeKind.status, id: status.id),
            loaded: LikeState(liked: status.liked, count: status.likesCount),
            builder: (context, like, onToggle) => TextButton.icon(
              onPressed: onToggle,
              style: like.liked ? plain(colors.primary) : muted,
              icon: Icon(like.liked ? Icons.thumb_up : Icons.thumb_up_outlined),
              label: Text(like.count > 0 ? formatCount(like.count) : '赞'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _content(
    BuildContext context,
    Status status,
    AsyncValue<Status> detail, {
    required bool split,
  }) {
    final actions = statusCardActions(context);
    final authorId = status.user?.id;
    final session = ref.watch(sessionProvider);
    final me = session is SignedIn ? session.account.uid : null;
    final comments = {
      for (final order in _openedOrders)
        if (_commentsQuery(status, order) case final query?)
          order: ref.watch(commentsProvider(query)),
    };
    final reposts = _repostsOpened
        ? ref.watch(repostsProvider(status.id))
        : null;

    final Widget list;
    final VoidCallback? loadMore;
    final activeQuery = _commentsQuery(status, _order);
    if (_tab == _Tab.reposts) {
      final provider = repostsProvider(status.id);
      list = PagedSliverList<Status>(
        value: reposts!,
        itemBuilder: (context, repost) => _RepostTile(repost, actions: actions),
        emptyText: '还没有人转发',
        onRetry: () => ref.invalidate(provider),
        onLoadMore: () => ref.read(provider.notifier).loadMore(),
      );
      loadMore = reposts.value?.loadMoreError == null
          ? ref.read(provider.notifier).loadMore
          : null;
    } else if (activeQuery != null) {
      final provider = commentsProvider(activeQuery);
      final value = comments[_order]!;
      list = PagedSliverList<Comment>(
        value: value,
        itemBuilder: (context, comment) => CommentTile(
          comment,
          authorId: authorId,
          actions: actions,
          onThread: () => context.push(
            '/comments/${comment.id}',
            extra: CommentThread(
              root: comment,
              statusId: status.id,
              authorId: activeQuery.authorId,
            ),
          ),
          onTap: comment.canReply ? () => _reply(status, comment) : null,
          onLongPress: me != null && comment.user?.id == me
              ? () => _delete(status, comment)
              : null,
        ),
        emptyText: '还没有评论',
        onRetry: () => ref.invalidate(provider),
        onLoadMore: () => ref.read(provider.notifier).loadMore(),
      );
      loadMore = value.value?.loadMoreError == null
          ? ref.read(provider.notifier).loadMore
          : null;
    } else {
      list = const SliverToBoxAdapter(
        child: Padding(
          padding: EdgeInsets.all(40),
          child: Center(child: Text('无法加载这条微博的评论')),
        ),
      );
      loadMore = null;
    }

    final post = [
      SliverToBoxAdapter(
        child: StatusCard(
          status,
          detail: true,
          actions: _cardActions(status, actions),
        ),
      ),
      // The full text failed to load; the card shows what the previous page
      // had.
      if (detail.hasError && !detail.hasValue)
        SliverToBoxAdapter(
          child: _LoadFailedBar(
            message: loadErrorMessage(detail.error),
            onRetry: () => ref.invalidate(statusDetailProvider(widget.id)),
          ),
        ),
    ];
    final responses = [
      SliverPersistentHeader(
        pinned: true,
        delegate: _TabsDelegate(
          status: status,
          repostsCount: _repostsCount(status),
          commentsCount: _commentsCount(status),
          tab: _tab,
          onTab: _showTab,
          colors: Theme.of(context).colorScheme,
        ),
      ),
      if (_tab == _Tab.comments && activeQuery != null)
        SliverToBoxAdapter(
          child: _OrderSwitch(order: _order, onChanged: _showOrder),
        ),
      list,
    ];

    Widget scrollView(List<Widget> slivers, {VoidCallback? onNearEnd}) =>
        RefreshIndicator(
          onRefresh: () => _refresh(status),
          child: LoadMoreTrigger(
            onNearEnd: onNearEnd,
            child: CustomScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                ReadingSlivers(
                  slivers: [
                    ...slivers,
                    // Room to scroll the last item out from under the
                    // comment bar.
                    SliverToBoxAdapter(
                      child: SizedBox(
                        height: MediaQuery.paddingOf(context).bottom,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );

    return split
        ? ReadingPanes(
            start: scrollView(post),
            end: scrollView(responses, onNearEnd: loadMore),
          )
        : scrollView([...post, ...responses], onNearEnd: loadMore);
  }

  /// [base] for the status atop the page: its own "展开" stays inert, as the
  /// full text is shown or on its way.
  StatusCardActions _cardActions(Status status, StatusCardActions base) =>
      base.copyWith(
        onExpand: (s) {
          if (s.id != status.id) base.onExpand?.call(s);
        },
        // The page has nothing left to show.
        onDelete: (s) => deleteOwnStatus(
          context,
          s,
          onDeleted: () {
            if (mounted) Navigator.maybePop(context);
          },
        ),
      );
}

class _LoadFailedBar extends StatelessWidget {
  const _LoadFailedBar({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ColoredBox(
      color: theme.colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.only(left: 16, right: 8),
        child: Row(
          children: [
            Expanded(
              child: Text(
                '正文加载失败：$message',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onErrorContainer,
                ),
              ),
            ),
            TextButton(onPressed: onRetry, child: const Text('重试')),
          ],
        ),
      ),
    );
  }
}

/// The repost and comment tabs with their counts, and the like count (the
/// like button is in the bottom bar).
class _TabsDelegate extends SliverPersistentHeaderDelegate {
  _TabsDelegate({
    required this.status,
    required this.repostsCount,
    required this.commentsCount,
    required this.tab,
    required this.onTab,
    required this.colors,
  });

  final Status status;

  /// With what the user added or deleted here.
  final int repostsCount;
  final int commentsCount;
  final _Tab tab;
  final ValueChanged<_Tab> onTab;

  // Part of the delegate so a theme change rebuilds the header.
  final ColorScheme colors;

  static const _height = 44.0;

  @override
  double get minExtent => _height;

  @override
  double get maxExtent => _height;

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlaps) {
    final theme = Theme.of(context);
    Widget tabButton(_Tab value, String label, int count) {
      final selected = value == tab;
      return InkWell(
        onTap: () => onTab(value),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const SizedBox(height: 3),
              Text(
                '$label ${formatCount(count)}',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: selected ? colors.onSurface : colors.onSurfaceVariant,
                  fontWeight: selected ? FontWeight.w600 : null,
                ),
              ),
              const SizedBox(height: 4),
              Container(
                width: 20,
                height: 3,
                decoration: BoxDecoration(
                  color: selected ? colors.primary : Colors.transparent,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ],
          ),
        ),
      );
    }

    return Material(
      color: colors.surface,
      child: Column(
        children: [
          Expanded(
            child: Row(
              children: [
                tabButton(_Tab.reposts, '转发', repostsCount),
                tabButton(_Tab.comments, '评论', commentsCount),
                const Spacer(),
                Padding(
                  padding: const EdgeInsets.only(right: 16),
                  child: LikeBuilder(
                    target: (kind: LikeKind.status, id: status.id),
                    loaded: LikeState(
                      liked: status.liked,
                      count: status.likesCount,
                    ),
                    builder: (context, like, _) => Text(
                      '赞 ${formatCount(like.count)}',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: colors.onSurfaceVariant,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
        ],
      ),
    );
  }

  @override
  bool shouldRebuild(_TabsDelegate oldDelegate) =>
      oldDelegate.status != status ||
      oldDelegate.repostsCount != repostsCount ||
      oldDelegate.commentsCount != commentsCount ||
      oldDelegate.tab != tab ||
      oldDelegate.colors != colors;
}

class _OrderSwitch extends StatelessWidget {
  const _OrderSwitch({required this.order, required this.onChanged});

  final CommentOrder order;
  final ValueChanged<CommentOrder> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    Widget option(CommentOrder value, String label) {
      final selected = value == order;
      return InkWell(
        onTap: selected ? null : () => onChanged(value),
        borderRadius: BorderRadius.circular(4),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 6),
          child: Text(
            label,
            style: theme.textTheme.bodySmall?.copyWith(
              color: selected
                  ? theme.colorScheme.primary
                  : theme.colorScheme.onSurfaceVariant,
              fontWeight: selected ? FontWeight.w600 : null,
            ),
          ),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.fromLTRB(10, 4, 10, 0),
      child: Row(
        children: [
          option(CommentOrder.hot, '按热度'),
          option(CommentOrder.time, '按时间'),
        ],
      ),
    );
  }
}

class _RepostTile extends StatelessWidget {
  const _RepostTile(this.status, {required this.actions});

  final Status status;
  final StatusCardActions actions;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final user = status.user;
    final created = status.createdAt;
    final onOpen = actions.onOpen;
    return InkWell(
      onTap: onOpen == null ? null : () => onOpen(status),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            UserAvatar(user, size: 32, onTap: actions.onUser),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    user?.screenName ?? '微博用户',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: muted,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 4),
                  StatusText(
                    status.text,
                    urls: status.urls,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontSize: 15,
                      height: 1.45,
                    ),
                    onLink: actions.onLink,
                  ),
                  if (created != null) ...[
                    const SizedBox(height: 6),
                    Text(
                      formatPostTime(created),
                      style: theme.textTheme.bodySmall?.copyWith(color: muted),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

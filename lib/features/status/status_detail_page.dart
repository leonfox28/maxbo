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
import '../../widgets/load_state.dart';
import '../../widgets/rich_text/status_text.dart';
import '../../widgets/status_card/status_card.dart';
import '../../widgets/user_avatar.dart';
import '../comments/comment_replies_page.dart';
import '../comments/comment_tile.dart';
import '../comments/comments_controller.dart';
import '../interactions/comment_composer.dart';
import '../interactions/likes.dart';
import 'status_actions.dart';
import 'status_detail_providers.dart';

/// A status with its full text, and its comments and reposts in tabs.
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

class _StatusDetailPageState extends ConsumerState<StatusDetailPage> {
  var _tab = _Tab.comments;
  var _order = CommentOrder.hot;

  // Lists opened so far stay loaded while the page is open, so going back to
  // one doesn't fetch it again.
  final _openedOrders = {CommentOrder.hot};
  var _repostsOpened = false;

  /// Comments the user added here, less those they deleted, since the
  /// status they were counted on loaded: newer data already has them.
  ({Status on, int added})? _commentsChange;
  final _drafts = <String, String>{};
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

  int _commentsAddedTo(Status status) {
    final change = _commentsChange;
    return change != null && identical(change.on, status) ? change.added : 0;
  }

  int _commentsCount(Status status) =>
      max(0, status.commentsCount + _commentsAddedTo(status));

  void _countComments(Status status, int change) => setState(
    () => _commentsChange = (
      on: status,
      added: _commentsAddedTo(status) + change,
    ),
  );

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
      hint: '写评论…',
      drafts: _drafts,
      draftKey: 'status',
      send: (text) async {
        final comment = await ref
            .read(weiboApiProvider)
            .postComment(statusId: status.id, text: text);
        if (!mounted) return;
        _countComments(status, 1);
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
      hint: name == null ? '回复评论…' : '回复 @$name',
      drafts: _drafts,
      draftKey: target.id,
      send: (text) async {
        final reply = await ref
            .read(weiboApiProvider)
            .postComment(statusId: status.id, text: text, replyTo: target.id);
        if (!mounted) return;
        // Weibo counts replies among a status's comments.
        _countComments(status, 1);
        _editComments(
          status,
          (list) => list.replace(target.id, (c) => withReply(c, reply)),
        );
      },
    );
    if (sent && mounted) _showSnackBar('回复成功');
  }

  Future<void> _delete(Status status, Comment comment) async {
    if (!await confirmDeleteComment(context) || !mounted) return;
    if (!_deleting.add(comment.id)) return;
    try {
      await ref.read(weiboApiProvider).deleteComment(comment.id);
      if (!mounted) return;
      _countComments(status, -1);
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
    return Scaffold(
      appBar: AppBar(title: const Text('微博正文')),
      bottomNavigationBar: status == null || status.user == null
          ? null
          : _bottomBar(status),
      body: status != null
          ? _content(context, status, detail)
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
    return CommentBar(
      hint: '写评论…',
      onTap: () => _comment(status),
      trailing: LikeBuilder(
        target: (kind: LikeKind.status, id: status.id),
        loaded: LikeState(liked: status.liked, count: status.likesCount),
        builder: (context, like, onToggle) => TextButton.icon(
          onPressed: onToggle,
          style: like.liked
              ? TextButton.styleFrom(foregroundColor: colors.primary)
              : TextButton.styleFrom(foregroundColor: colors.onSurfaceVariant),
          icon: Icon(like.liked ? Icons.thumb_up : Icons.thumb_up_outlined),
          label: Text(like.count > 0 ? formatCount(like.count) : '赞'),
        ),
      ),
    );
  }

  Widget _content(
    BuildContext context,
    Status status,
    AsyncValue<Status> detail,
  ) {
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

    return RefreshIndicator(
      onRefresh: () => _refresh(status),
      child: LoadMoreTrigger(
        onNearEnd: loadMore,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: StatusCard(
                status,
                detail: true,
                actions: _cardActions(status, actions),
              ),
            ),
            // The full text failed to load; the card shows what the
            // previous page had.
            if (detail.hasError && !detail.hasValue)
              SliverToBoxAdapter(
                child: _LoadFailedBar(
                  message: loadErrorMessage(detail.error),
                  onRetry: () =>
                      ref.invalidate(statusDetailProvider(widget.id)),
                ),
              ),
            SliverPersistentHeader(
              pinned: true,
              delegate: _TabsDelegate(
                status: status,
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
          ],
        ),
      ),
    );
  }

  /// [base] for the status atop the page: its own "展开" stays inert, as the
  /// full text is shown or on its way.
  StatusCardActions _cardActions(Status status, StatusCardActions base) =>
      base.copyWith(
        onExpand: (s) {
          if (s.id != status.id) base.onExpand?.call(s);
        },
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
    required this.commentsCount,
    required this.tab,
    required this.onTab,
    required this.colors,
  });

  final Status status;

  /// With the comments the user added or deleted here.
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
                tabButton(_Tab.reposts, '转发', status.repostsCount),
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

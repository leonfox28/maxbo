import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/util/format.dart';
import '../../data/models/comment.dart';
import '../../data/models/status.dart';
import '../../widgets/load_state.dart';
import '../../widgets/rich_text/status_text.dart';
import '../../widgets/status_card/status_card.dart';
import '../../widgets/user_avatar.dart';
import '../comments/comment_replies_page.dart';
import '../comments/comment_tile.dart';
import '../comments/comments_controller.dart';
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
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(statusDetailProvider(widget.id));
    final status = detail.value ?? widget.initial;
    return Scaffold(
      appBar: AppBar(title: const Text('微博正文')),
      body: status != null
          ? _content(context, status, detail)
          : detail.hasError
          ? ErrorRetry(
              message: loadErrorMessage(detail.error),
              onRetry: () => ref.invalidate(statusDetailProvider(widget.id)),
            )
          : const Center(child: CircularProgressIndicator()),
    );
  }

  Widget _content(
    BuildContext context,
    Status status,
    AsyncValue<Status> detail,
  ) {
    final actions = statusCardActions(context);
    final authorId = status.user?.id;
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
            extra: CommentThread(root: comment, authorId: activeQuery.authorId),
          ),
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
      StatusCardActions(
        onOpen: base.onOpen,
        onLink: base.onLink,
        onUser: base.onUser,
        onExpand: (s) {
          if (s.id != status.id) base.onExpand?.call(s);
        },
        onPicture: base.onPicture,
        onVideo: base.onVideo,
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

/// The repost and comment tabs with their counts, and the like count, which
/// is display only.
class _TabsDelegate extends SliverPersistentHeaderDelegate {
  _TabsDelegate({
    required this.status,
    required this.tab,
    required this.onTab,
    required this.colors,
  });

  final Status status;
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
                tabButton(_Tab.comments, '评论', status.commentsCount),
                const Spacer(),
                Padding(
                  padding: const EdgeInsets.only(right: 16),
                  child: Text(
                    '赞 ${formatCount(status.likesCount)}',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: colors.onSurfaceVariant,
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

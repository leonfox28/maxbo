import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/models/comment.dart';
import '../../widgets/load_state.dart';
import '../status/status_actions.dart';
import 'comment_tile.dart';
import 'comments_controller.dart';

/// What the replies page shows: the thread of [root], on a status by
/// [authorId] (Weibo's comment API asks for the author).
class CommentThread {
  const CommentThread({required this.root, required this.authorId});

  final Comment root;
  final String authorId;
}

/// A comment and all the replies in its thread ("楼中楼").
class CommentRepliesPage extends ConsumerWidget {
  const CommentRepliesPage({super.key, required this.thread});

  final CommentThread thread;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final actions = statusCardActions(context);
    final provider = repliesProvider((
      commentId: thread.root.id,
      authorId: thread.authorId,
    ));
    final replies = ref.watch(provider);
    final total = replies.value?.total ?? thread.root.replyCount;

    return Scaffold(
      appBar: AppBar(title: const Text('评论详情')),
      body: LoadMoreTrigger(
        onNearEnd: replies.value?.loadMoreError == null
            ? ref.read(provider.notifier).loadMore
            : null,
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: CommentTile(
                thread.root,
                authorId: thread.authorId,
                actions: actions,
              ),
            ),
            SliverToBoxAdapter(
              child: Container(
                height: 8,
                color: theme.colorScheme.surfaceContainer,
              ),
            ),
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                child: Text('$total 条回复', style: theme.textTheme.titleSmall),
              ),
            ),
            PagedSliverList<Comment>(
              value: replies,
              itemBuilder: (context, reply) => CommentTile(
                reply,
                authorId: thread.authorId,
                actions: actions,
              ),
              emptyText: '还没有回复',
              onRetry: () => ref.invalidate(provider),
              onLoadMore: () => ref.read(provider.notifier).loadMore(),
            ),
          ],
        ),
      ),
    );
  }
}

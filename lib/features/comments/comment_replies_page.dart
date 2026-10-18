import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/comment.dart';
import '../../widgets/adaptive_layout.dart';
import '../../widgets/load_state.dart';
import '../interactions/comment_composer.dart';
import '../status/status_actions.dart';
import 'comment_tile.dart';
import 'comments_controller.dart';

/// What the replies page shows: the thread of [root], on status [statusId]
/// by [authorId] (Weibo's comment API asks for the author).
class CommentThread {
  const CommentThread({
    required this.root,
    required this.statusId,
    required this.authorId,
  });

  final Comment root;
  final String statusId;
  final String authorId;
}

/// A comment and all the replies in its thread ("楼中楼"), where the user
/// can reply to any of them.
class CommentRepliesPage extends ConsumerStatefulWidget {
  const CommentRepliesPage({super.key, required this.thread});

  final CommentThread thread;

  @override
  ConsumerState<CommentRepliesPage> createState() => _CommentRepliesPageState();
}

class _CommentRepliesPageState extends ConsumerState<CommentRepliesPage> {
  final _drafts = <String, String>{};
  final _deleting = <String>{};

  CommentThread get _thread => widget.thread;

  RepliesQuery get _query =>
      (commentId: _thread.root.id, authorId: _thread.authorId);

  /// Shows a change of the thread on the status page below too.
  void _editRoot(Comment Function(Comment root) change) => editOpenCommentLists(
    ref,
    statusId: _thread.statusId,
    authorId: _thread.authorId,
    edit: (list) => list.replace(_thread.root.id, change),
  );

  Future<void> _reply(Comment target) async {
    final name = target.user?.screenName;
    final sent = await showCommentComposer(
      context,
      hint: name == null ? '回复评论…' : '回复 @$name',
      drafts: _drafts,
      draftKey: target.id,
      send: (text) async {
        final reply = await ref
            .read(weiboApiProvider)
            .postComment(
              statusId: _thread.statusId,
              text: text,
              replyTo: target.id,
            );
        if (!mounted) return;
        // Replies list newest first.
        if (reply != null) {
          ref.read(repliesProvider(_query).notifier).prepend(reply);
        }
        _editRoot((root) => withReply(root, reply));
      },
    );
    if (sent && mounted) _showSnackBar('回复成功');
  }

  Future<void> _delete(Comment reply) async {
    if (!await confirmDelete(context) || !mounted) return;
    if (!_deleting.add(reply.id)) return;
    try {
      await ref.read(weiboApiProvider).deleteComment(reply.id);
      if (!mounted) return;
      ref.read(repliesProvider(_query).notifier).remove(reply.id);
      _editRoot((root) => withoutReply(root, reply.id));
      _showSnackBar('已删除');
    } on WeiboException catch (e) {
      if (mounted) showErrorSnackBar(context, e);
    } finally {
      _deleting.remove(reply.id);
    }
  }

  void _showSnackBar(String message) => ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final actions = statusCardActions(context);
    final root = _thread.root;
    final provider = repliesProvider(_query);
    final replies = ref.watch(provider);
    final total = replies.value?.total ?? root.replyCount;
    final session = ref.watch(sessionProvider);
    final me = session is SignedIn ? session.account.uid : null;
    final rootName = root.user?.screenName;

    return Scaffold(
      appBar: AppBar(title: const Text('评论详情')),
      bottomNavigationBar: root.canReply
          ? CommentBar(
              hint: rootName == null ? '回复评论…' : '回复 @$rootName',
              onTap: () => _reply(root),
            )
          : null,
      body: LoadMoreTrigger(
        onNearEnd: replies.value?.loadMoreError == null
            ? ref.read(provider.notifier).loadMore
            : null,
        child: CustomScrollView(
          slivers: [
            ReadingSlivers(
              slivers: [
                SliverToBoxAdapter(
                  child: CommentTile(
                    root,
                    authorId: _thread.authorId,
                    actions: actions,
                    onTap: root.canReply ? () => _reply(root) : null,
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
                    child: Text(
                      '$total 条回复',
                      style: theme.textTheme.titleSmall,
                    ),
                  ),
                ),
                PagedSliverList<Comment>(
                  value: replies,
                  itemBuilder: (context, reply) => CommentTile(
                    reply,
                    authorId: _thread.authorId,
                    actions: actions,
                    onTap: reply.canReply ? () => _reply(reply) : null,
                    onLongPress: me != null && reply.user?.id == me
                        ? () => _delete(reply)
                        : null,
                  ),
                  emptyText: '还没有回复',
                  onRetry: () => ref.invalidate(provider),
                  onLoadMore: () => ref.read(provider.notifier).loadMore(),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

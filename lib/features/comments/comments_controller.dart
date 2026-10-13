import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/paging/paged_controller.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/comment.dart';
import '../../data/models/paged.dart';

/// Which comments to list: those on status [statusId] by [authorId], in
/// [order].
typedef CommentsQuery = ({
  String statusId,
  String authorId,
  CommentOrder order,
});

/// Which thread to list: the replies to comment [commentId] on a status by
/// [authorId].
typedef RepliesQuery = ({String commentId, String authorId});

final commentsProvider = AsyncNotifierProvider.autoDispose
    .family<CommentsController, PagedState<Comment>, CommentsQuery>(
      CommentsController.new,
    );

/// Applies [edit] to the lists of comments on status [statusId] (by
/// [authorId]) that pages have open. Lists not open aren't loaded for it.
void editOpenCommentLists(
  WidgetRef ref, {
  required String statusId,
  required String authorId,
  required void Function(CommentsController list) edit,
}) {
  for (final order in CommentOrder.values) {
    final provider = commentsProvider((
      statusId: statusId,
      authorId: authorId,
      order: order,
    ));
    if (ref.exists(provider)) edit(ref.read(provider.notifier));
  }
}

/// [comment] with [reply] (if Weibo returned it) added to its thread.
Comment withReply(Comment comment, Comment? reply) => comment.copyWith(
  replyCount: comment.replyCount + 1,
  // The preview shows the first two replies.
  replies: reply != null && comment.replies.length < 2
      ? [...comment.replies, reply]
      : null,
);

/// [comment] with reply [id] deleted from its thread.
Comment withoutReply(Comment comment, String id) => comment.copyWith(
  replyCount: max(0, comment.replyCount - 1),
  replies: [
    for (final r in comment.replies)
      if (r.id != id) r,
  ],
);

final repliesProvider = AsyncNotifierProvider.autoDispose
    .family<RepliesController, PagedState<Comment>, RepliesQuery>(
      RepliesController.new,
    );

class CommentsController extends PagedController<Comment> {
  CommentsController(this.query);

  final CommentsQuery query;

  @override
  Future<Paged<Comment>> fetchPage(String? cursor) => ref
      .read(weiboApiProvider)
      .fetchComments(
        statusId: query.statusId,
        authorId: query.authorId,
        order: query.order,
        maxId: cursor,
      );

  @override
  String idOf(Comment item) => item.id;
}

class RepliesController extends PagedController<Comment> {
  RepliesController(this.query);

  final RepliesQuery query;

  @override
  Future<Paged<Comment>> fetchPage(String? cursor) => ref
      .read(weiboApiProvider)
      .fetchReplies(
        commentId: query.commentId,
        authorId: query.authorId,
        maxId: cursor,
      );

  @override
  String idOf(Comment item) => item.id;
}

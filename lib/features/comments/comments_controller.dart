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

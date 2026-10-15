import '../../core/util/format.dart';
import '../../core/util/json_read.dart';
import 'comment.dart';
import 'status.dart';
import 'weibo_user.dart';

/// The lists of weibo.com's 消息 page that maxbo shows.
enum NoticeKind {
  /// Comments on the user's posts and replies to their comments.
  comments,

  /// Posts that mention the user.
  mentionStatuses,

  /// Comments that mention the user.
  mentionComments,

  /// Likes of the user's posts and comments.
  likes,
}

/// Which [NoticeKind]s have something the user hasn't seen.
class UnreadNotices {
  const UnreadNotices([this.kinds = const {}]);

  final Set<NoticeKind> kinds;

  bool get any => kinds.isNotEmpty;

  bool has(NoticeKind kind) => kinds.contains(kind);

  UnreadNotices without(NoticeKind kind) =>
      UnreadNotices({...kinds}..remove(kind));

  /// Reads the counts of `remind/push_count`, which sit at the top level and
  /// may leave out the ones that are 0 (PLAN §8).
  factory UnreadNotices.fromJson(Map<String, Object?> json) {
    bool counts(List<String> keys) =>
        keys.any((k) => (readInt(json[k]) ?? 0) > 0);
    return UnreadNotices({
      if (counts(['cmt'])) NoticeKind.comments,
      if (counts(['mention_status', 'all_mention_status']))
        NoticeKind.mentionStatuses,
      if (counts(['mention_cmt', 'all_mention_cmt']))
        NoticeKind.mentionComments,
      if (counts(['attitude'])) NoticeKind.likes,
    });
  }
}

/// A comment in the notifications, with the post it is on and, for a
/// reply, the comment it answers.
class CommentNotice {
  const CommentNotice({required this.comment, this.status, this.replyTo});

  final Comment comment;
  final Status? status;
  final Comment? replyTo;

  String get id => comment.id;

  static CommentNotice? tryFromJson(Object? json) {
    final map = readMap(json);
    final comment = Comment.tryFromJson(map);
    if (map == null || comment == null) return null;
    return CommentNotice(
      comment: comment,
      status: Status.tryFromJson(map['status']),
      replyTo: Comment.tryFromJson(map['reply_comment']),
    );
  }
}

/// Someone liking one of the user's posts or comments.
class LikeNotice {
  const LikeNotice({
    required this.id,
    this.user,
    this.createdAt,
    this.status,
    this.comment,
    this.statusComplete = false,
  });

  final String id;

  /// Who liked it.
  final WeiboUser? user;
  final DateTime? createdAt;

  /// The post liked, or the one [comment] is on.
  final Status? status;

  /// The comment liked, if it was a comment.
  final Comment? comment;

  /// False when [status] is the cut-down form Weibo sometimes sends, without
  /// pictures or counts (PLAN §8), which the detail page shouldn't show
  /// while it loads the whole post.
  final bool statusComplete;

  static LikeNotice? tryFromJson(Object? json) {
    final map = readMap(json);
    if (map == null) return null;
    // `idStr`, unlike everywhere else.
    final id =
        readString(map['idStr']) ??
        readString(map['idstr']) ??
        readString(map['id']);
    if (id == null) return null;
    final comment = readMap(map['comment']);
    final statusJson = readMap(map['status']) ?? readMap(comment?['status']);
    return LikeNotice(
      id: id,
      user: WeiboUser.tryFromJson(map['user']),
      createdAt: parseWeiboDate(readString(map['created_at'])),
      status: Status.tryFromJson(statusJson),
      comment: Comment.tryFromJson(comment),
      statusComplete: statusJson?.containsKey('attitudes_count') ?? false,
    );
  }
}

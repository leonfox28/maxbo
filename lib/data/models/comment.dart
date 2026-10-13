import '../../core/util/format.dart';
import '../../core/util/json_read.dart';
import 'pic_info.dart';
import 'status.dart';
import 'weibo_user.dart';

/// How a status's comments are sorted.
enum CommentOrder { hot, time }

/// A comment on a status, or a reply in a comment's thread ("楼中楼").
class Comment {
  const Comment({
    required this.id,
    this.user,
    this.createdAt,
    this.source,
    this.text = '',
    this.urls = const [],
    this.pics = const [],
    this.likesCount = 0,
    this.liked = false,
    this.replyCount = 0,
    this.replies = const [],
    this.canReply = true,
  });

  final String id;
  final WeiboUser? user;
  final DateTime? createdAt;

  /// Where it was posted from, e.g. `来自上海`.
  final String? source;

  /// The body as HTML, like `Status.text`. Replies in a thread start with
  /// `回复@name:` themselves.
  final String text;
  final List<UrlEntity> urls;

  /// Attached pictures, which [text] doesn't mention.
  final List<PicInfo> pics;

  final int likesCount;
  final bool liked;

  /// How many replies its thread has; [replies] previews the first few.
  final int replyCount;
  final List<Comment> replies;

  /// False when its author turned replies off.
  final bool canReply;

  Comment copyWith({int? replyCount, List<Comment>? replies}) => Comment(
    id: id,
    user: user,
    createdAt: createdAt,
    source: source,
    text: text,
    urls: urls,
    pics: pics,
    likesCount: likesCount,
    liked: liked,
    replyCount: replyCount ?? this.replyCount,
    replies: replies ?? this.replies,
    canReply: canReply,
  );

  static Comment? tryFromJson(Object? json) {
    final map = readMap(json);
    if (map == null) return null;
    final id = readString(map['idstr']) ?? readString(map['id']);
    if (id == null) return null;
    // Pictures come as url_struct entries, each with its own pic_infos.
    final urlStruct = readMaps(map['url_struct']);
    return Comment(
      id: id,
      user: WeiboUser.tryFromJson(map['user']),
      createdAt: parseWeiboDate(readString(map['created_at'])),
      source: readString(map['source'])?.trim(),
      text: readString(map['text']) ?? '',
      urls: [for (final u in urlStruct) ?UrlEntity.tryFromJson(u)],
      pics: [for (final u in urlStruct) ...picsOf(u)],
      likesCount: readInt(map['like_counts']) ?? 0,
      liked: map['liked'] == true,
      replyCount: readInt(map['total_number']) ?? 0,
      replies: [
        for (final r in readMaps(map['comments'])) ?Comment.tryFromJson(r),
      ],
      canReply: readInt(map['disable_reply']) != 1,
    );
  }
}

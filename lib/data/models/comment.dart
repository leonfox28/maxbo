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
    final urlStruct = readMaps(map['url_struct']);
    final pics = _ownPics(map, urlStruct);
    var text = readString(map['text']) ?? '';
    // A comment just sent names its pictures only in pic_ids, and its text
    // ends with a short link to each, which url_struct would otherwise
    // have hidden.
    if (pics.isNotEmpty && !urlStruct.any((u) => u['pic_infos'] != null)) {
      for (var i = 0; i < pics.length; i++) {
        text = text.replaceFirst(_trailingShortLink, '');
      }
    }
    return Comment(
      id: id,
      user: WeiboUser.tryFromJson(map['user']),
      createdAt: parseWeiboDate(readString(map['created_at'])),
      source: readString(map['source'])?.trim(),
      text: text,
      urls: [for (final u in urlStruct) ?UrlEntity.tryFromJson(u)],
      pics: pics,
      likesCount: readInt(map['like_counts']) ?? 0,
      liked: map['liked'] == true,
      replyCount: readInt(map['total_number']) ?? 0,
      replies: [
        for (final r in readMaps(map['comments'])) ?Comment.tryFromJson(r),
      ],
      canReply: readInt(map['disable_reply']) != 1,
    );
  }

  static final _trailingShortLink = RegExp(
    r'\s*<a\s[^>]*href="?https?://t\.cn/[^>]*>(?:(?!</a>).)*</a>\s*$',
    dotAll: true,
  );

  // Pictures come as url_struct entries, each with its own pic_infos. A
  // reply also carries the entry of a picture on the comment it answers, but
  // has pic_num 0 then; its own pictures are linked from its text_raw.
  // A comment just sent has no url_struct, only the pids in pic_ids.
  static List<PicInfo> _ownPics(
    Map<String, Object?> map,
    List<Map<String, Object?>> urlStruct,
  ) {
    final count = readInt(map['pic_num']);
    if (count == 0) return const [];
    final entries = urlStruct.where((u) => u['pic_infos'] != null).toList();
    if (entries.isEmpty) {
      final pids = map['pic_ids'] is List ? map['pic_ids']! as List : const [];
      return [
        for (final pid in pids.map(readString).nonNulls)
          ?PicInfo.tryFromPid(pid),
      ];
    }
    final raw = readString(map['text_raw']);
    final linked = [
      for (final u in entries)
        if (readString(u['short_url']) case final short?
            when raw?.contains(short) ?? false)
          u,
    ];
    return [for (final u in linked.isEmpty ? entries : linked) ...picsOf(u)];
  }
}

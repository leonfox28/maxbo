import 'package:html/parser.dart' as html;

import '../../core/util/format.dart';
import '../../core/util/json_read.dart';
import 'pic_info.dart';
import 'weibo_user.dart';

/// A Weibo post ("微博").
class Status {
  const Status({
    required this.id,
    this.mblogId,
    this.user,
    this.createdAt,
    this.source,
    this.region,
    this.text = '',
    this.urls = const [],
    this.pics = const [],
    this.picCount = 0,
    this.video,
    this.pageCard,
    this.retweeted,
    this.repostsCount = 0,
    this.commentsCount = 0,
    this.likesCount = 0,
    this.liked = false,
    this.isAd = false,
  });

  final String id;

  /// The short id used in web URLs (`weibo.com/<uid>/<mblogid>`).
  final String? mblogId;

  /// Null for deleted or hidden posts, which still appear as retweet sources.
  final WeiboUser? user;
  final DateTime? createdAt;

  /// The posting client, e.g. `iPhone客户端`; plain text.
  final String? source;

  /// Where it was posted from, e.g. `发布于 北京`.
  final String? region;

  /// The body as HTML; see `parseStatusText`.
  final String text;

  /// Short links in [text] and what they resolve to.
  final List<UrlEntity> urls;
  final List<PicInfo> pics;

  /// How many pictures the post has, which can exceed [pics].
  final int picCount;
  final VideoInfo? video;

  /// A link preview (topic, live stream, …) shown when there is no media.
  final PageCard? pageCard;
  final Status? retweeted;

  final int repostsCount;
  final int commentsCount;
  final int likesCount;
  final bool liked;

  /// Marked as an ad by Weibo. Ads are labelled, never hidden (PLAN §2).
  final bool isAd;

  static Status? tryFromJson(Object? json) {
    final map = readMap(json);
    if (map == null) return null;
    final id =
        readString(map['idstr']) ??
        readString(map['mid']) ??
        readString(map['id']);
    if (id == null) return null;

    final infos = readMap(map['pic_infos']);
    final pics = <PicInfo>[];
    if (infos != null) {
      // pic_ids holds the display order; pic_infos is keyed by id.
      final order = map['pic_ids'] is List
          ? (map['pic_ids']! as List).map(readString).nonNulls
          : const <String>[];
      final ids = order.isEmpty ? infos.keys : order;
      for (final pid in ids) {
        if (PicInfo.tryFromJson(infos[pid]) case final pic?) pics.add(pic);
      }
    }
    final pageInfo = readMap(map['page_info']);

    return Status(
      id: id,
      mblogId: readString(map['mblogid']),
      user: WeiboUser.tryFromJson(map['user']),
      createdAt: parseWeiboDate(readString(map['created_at'])),
      source: _plainText(readString(map['source'])),
      region: readString(map['region_name']),
      text: readString(map['text']) ?? '',
      urls: [
        for (final u in readMaps(map['url_struct'])) ?UrlEntity.tryFromJson(u),
      ],
      pics: pics,
      picCount: readInt(map['pic_num']) ?? pics.length,
      video: VideoInfo.tryFromPageInfo(pageInfo),
      pageCard: PageCard.tryFromPageInfo(pageInfo),
      retweeted: Status.tryFromJson(map['retweeted_status']),
      repostsCount: readInt(map['reposts_count']) ?? 0,
      commentsCount: readInt(map['comments_count']) ?? 0,
      likesCount: readInt(map['attitudes_count']) ?? 0,
      liked: readInt(map['attitudes_status']) == 1,
      isAd: _isAd(map),
    );
  }

  // Not yet confirmed against a real ad; see PLAN §8 item 5.
  static bool _isAd(Map<String, Object?> map) =>
      map['isAd'] == true ||
      map['readtimetype'] == 'adMblog' ||
      (readString(map['mark'])?.contains('mark_ad') ?? false);

  // `source` is plain text or an HTML fragment like `<a href=…>荣耀Magic</a>`.
  static String? _plainText(String? fragment) {
    if (fragment == null) return null;
    final text = fragment.contains('<')
        ? html.parseFragment(fragment).text?.trim()
        : fragment.trim();
    return text == null || text.isEmpty ? null : text;
  }
}

/// A short link (`t.cn/…`) in a status body.
class UrlEntity {
  const UrlEntity({required this.shortUrl, this.longUrl, this.title});

  final String shortUrl;
  final String? longUrl;
  final String? title;

  static UrlEntity? tryFromJson(Map<String, Object?> json) {
    final short = readUrl(json['short_url']);
    if (short == null) return null;
    return UrlEntity(
      shortUrl: short,
      longUrl: readUrl(json['long_url']) ?? readUrl(json['ori_url']),
      title: readString(json['url_title']),
    );
  }
}

/// The video attached to a status. Playback (M3) re-fetches the status for
/// fresh stream URLs, so only what the card shows is kept.
class VideoInfo {
  const VideoInfo({this.coverUrl, this.duration, this.aspectRatio});

  final String? coverUrl;

  /// Length in seconds.
  final int? duration;
  final double? aspectRatio;

  static VideoInfo? tryFromPageInfo(Map<String, Object?>? pageInfo) {
    if (pageInfo == null || pageInfo['object_type'] != 'video') return null;
    final media = readMap(pageInfo['media_info']);
    final big =
        readMap(readMap(media?['big_pic_info'])?['pic_big']) ??
        readMap(readMap(pageInfo['pic_info'])?['pic_big']);
    final width = readInt(big?['width']);
    final height = readInt(big?['height']);
    return VideoInfo(
      coverUrl: readUrl(pageInfo['page_pic']) ?? readUrl(big?['url']),
      duration: readInt(media?['duration']),
      aspectRatio: width != null && height != null && width > 0 && height > 0
          ? width / height
          : null,
    );
  }
}

/// A link preview such as a topic or live stream.
class PageCard {
  const PageCard({required this.title, this.description, this.imageUrl});

  final String title;
  final String? description;
  final String? imageUrl;

  static PageCard? tryFromPageInfo(Map<String, Object?>? pageInfo) {
    if (pageInfo == null || pageInfo['object_type'] == 'video') return null;
    final title = readString(pageInfo['page_title']);
    if (title == null) return null;
    return PageCard(
      title: title,
      description:
          readString(pageInfo['page_desc']) ?? readString(pageInfo['content2']),
      imageUrl: readUrl(pageInfo['page_pic']),
    );
  }
}

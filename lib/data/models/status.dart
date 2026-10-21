import 'dart:convert';

import 'package:html/parser.dart' as html;

import '../../core/util/format.dart';
import '../../core/util/json_read.dart';
import 'pic_info.dart';
import 'weibo_user.dart';

/// Who can see a post, as `visible.type` and `statuses/update` have it.
enum StatusVisibility {
  public(0, '公开'),
  friends(6, '好友圈'),
  onlyMe(1, '仅自己可见'),
  fans(10, '粉丝');

  const StatusVisibility(this.code, this.label);

  final int code;
  final String label;

  /// The ones a new post can choose from.
  static const choices = [public, friends, onlyMe];

  /// Unknown kinds (paid members, groups) count as public.
  static StatusVisibility of(int? code) =>
      values.firstWhere((v) => v.code == code, orElse: () => public);
}

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
    this.plainText,
    this.urls = const [],
    this.pics = const [],
    this.picCount = 0,
    this.mixedMedia = const [],
    this.video,
    this.pageCard,
    this.retweeted,
    this.repostsCount = 0,
    this.commentsCount = 0,
    this.likesCount = 0,
    this.liked = false,
    this.isAd = false,
    this.pinned = false,
    this.visibility = StatusVisibility.public,
  });

  final String id;

  /// The short id used in web URLs (`weibo.com/<uid>/<mblogid>`).
  final String? mblogId;

  /// Null for deleted or hidden posts, which still appear as retweet sources.
  /// Retweet sources in `statuses/mentions` have only an id and a name.
  final WeiboUser? user;
  final DateTime? createdAt;

  /// The posting client, e.g. `iPhone客户端`; plain text.
  final String? source;

  /// Where it was posted from, e.g. `发布于 北京`.
  final String? region;

  /// The body as HTML; see `parseStatusText`.
  final String text;

  /// The body as plain text, in full, as a repost of this one quotes it;
  /// null when Weibo sent only a cut-short one (long posts in lists).
  final String? plainText;

  /// Short links in [text] and what they resolve to.
  final List<UrlEntity> urls;
  final List<PicInfo> pics;

  /// How many pictures the post has, which can exceed [pics].
  final int picCount;

  /// The pictures and videos of a post mixing them, in order; such posts
  /// have neither [pics] nor [video].
  final List<MixedMedia> mixedMedia;
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

  /// Pinned atop its author's profile; only set in profile lists.
  final bool pinned;

  /// Who can see it. Others' posts are public as far as the viewer knows.
  final StatusVisibility visibility;

  /// [parentUrls] is the `url_struct` of the post retweeting this one,
  /// which also covers the links in this one's text, and [parentPage] its
  /// `page_info`.
  static Status? tryFromJson(
    Object? json, {
    List<Map<String, Object?>> parentUrls = const [],
    Map<String, Object?>? parentPage,
  }) {
    final map = readMap(json);
    if (map == null) return null;
    final id =
        readString(map['idstr']) ??
        readString(map['mid']) ??
        readString(map['id']);
    if (id == null) return null;

    final pics = picsOf(map);
    final pageInfo = readMap(map['page_info']);
    final ownUrls = readMaps(map['url_struct']);
    final urlStruct = ownUrls.isEmpty ? parentUrls : ownUrls;

    return Status(
      id: id,
      mblogId: readString(map['mblogid']),
      user: WeiboUser.tryFromJson(map['user']) ?? _authorOf(map, parentPage),
      createdAt: parseWeiboDate(readString(map['created_at'])),
      source: _plainText(readString(map['source'])),
      region: readString(map['region_name']),
      text: _textOf(map, urlStruct),
      plainText:
          readString(readMap(map['longText'])?['content']) ??
          (map['isLongText'] == true ? null : readString(map['text_raw'])),
      urls: [for (final u in urlStruct) ?UrlEntity.tryFromJson(u)],
      pics: pics,
      picCount: readInt(map['pic_num']) ?? pics.length,
      mixedMedia: [
        for (final item in readMaps(readMap(map['mix_media_info'])?['items']))
          ?MixedMedia.tryFromJson(item),
      ],
      video: VideoInfo.tryFromPageInfo(pageInfo),
      pageCard: PageCard.tryFromPageInfo(pageInfo, urlStruct: urlStruct),
      retweeted: Status.tryFromJson(
        map['retweeted_status'],
        parentUrls: ownUrls,
        parentPage: pageInfo,
      ),
      repostsCount: readInt(map['reposts_count']) ?? 0,
      commentsCount: readInt(map['comments_count']) ?? 0,
      likesCount: readInt(map['attitudes_count']) ?? 0,
      liked: readInt(map['attitudes_status']) == 1,
      isAd: _isAd(map),
      pinned: readInt(map['isTop']) == 1,
      visibility: StatusVisibility.of(
        readInt(readMap(map['visible'])?['type']),
      ),
    );
  }

  /// The videos of a post mixing pictures and videos, in order.
  List<VideoInfo> get mixedVideos => [
    for (final item in mixedMedia)
      if (item case MixedVideo(:final video)) video,
  ];

  /// Its video, or with an [index] the one at [index] in [mixedVideos].
  VideoInfo? videoAt(int? index) =>
      index == null ? video : mixedVideos.elementAtOrNull(index);

  // Not yet confirmed against a real feed ad; see PLAN §8 item 5. Weibo also
  // sets all of these on ordinary posts it classes as advertising content
  // (mark ending `WeiboADNatural`, seen on users' own reposts); those are
  // not ads placed in the feed, so they get no label.
  static bool _isAd(Map<String, Object?> map) {
    final mark = readString(map['mark']) ?? '';
    if (mark.contains('WeiboADNatural')) return false;
    return map['isAd'] == true ||
        map['readtimetype'] == 'adMblog' ||
        mark.contains('mark_ad');
  }

  // Retweet sources in `statuses/mentions` come without `user`. Their
  // retweeting post's page_info is a card for them instead, titled
  // `@author`, which weibo.com shows in their place (PLAN §8).
  static WeiboUser? _authorOf(
    Map<String, Object?> map,
    Map<String, Object?>? parentPage,
  ) {
    final mblogId = readString(map['mblogid']);
    if (parentPage == null ||
        mblogId == null ||
        readString(parentPage['mblogid']) != mblogId) {
      return null;
    }
    final id = readString(parentPage['uidPageInfo']);
    final title = readString(parentPage['content1']);
    if (id == null || title == null || !title.startsWith('@')) return null;
    final name = title.substring(1).trim();
    return name.isEmpty ? null : WeiboUser(id: id, screenName: name);
  }

  // Retweet sources in `statuses/mentions` come without `text` or `user`,
  // only the plain `text_raw`, cut short with no marker if the post is long
  // (PLAN §8).
  static String _textOf(
    Map<String, Object?> map,
    List<Map<String, Object?>> urlStruct,
  ) {
    if (readString(map['text']) case final text?) return text;
    final raw = readString(map['text_raw']);
    if (raw == null) return '';
    final html = _rawTextAsHtml(raw, urlStruct);
    return map['isLongText'] == true
        ? '$html ...<span class="expand">展开</span>'
        : html;
  }

  // Links, @mentions and #topics# in plain text. Links stop at the first
  // non-ASCII character: `text_raw` runs them straight into the text.
  static final _rawEntity = RegExp(
    r"https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+"
    r'|(?<![A-Za-z0-9])@[A-Za-z0-9_\-\u4e00-\u9fa5]{1,30}'
    r'|#[^#\n]{1,64}#',
  );

  /// [raw] as the HTML that `text` would have held, with links titled as
  /// `url_struct` describes them.
  static String _rawTextAsHtml(
    String raw,
    List<Map<String, Object?>> urlStruct,
  ) {
    const escape = HtmlEscape();
    final html = StringBuffer();
    var last = 0;
    for (final match in _rawEntity.allMatches(raw)) {
      html.write(escape.convert(raw.substring(last, match.start)));
      last = match.end;
      final entity = match[0]!;
      final label = escape.convert(entity);
      if (entity.startsWith('@')) {
        html.write('<a href="/n/${label.substring(1)}">$label</a>');
      } else if (entity.startsWith('#')) {
        final query = Uri.encodeQueryComponent(entity);
        html.write('<a href="https://s.weibo.com/weibo?q=$query">$label</a>');
      } else {
        final info = urlStruct
            .where((u) => readString(u['short_url']) == entity)
            .firstOrNull;
        // `url_type_pic` names icons that aren't there, so links go without.
        final title = readString(info?['url_title']);
        final text = title == null ? label : escape.convert(title);
        html.write('<a href="$label">$text</a>');
      }
    }
    html.write(escape.convert(raw.substring(last)));
    return '$html';
  }

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

/// The video attached to a status.
class VideoInfo {
  const VideoInfo({
    this.coverUrl,
    this.duration,
    this.aspectRatio,
    this.streamUrls = const [],
  });

  final String? coverUrl;

  /// Length in seconds.
  final int? duration;
  final double? aspectRatio;

  /// Playable mp4 streams, preferred first. They are signed and expire about
  /// an hour after Weibo sent them; see `resolveVideoUrl`.
  final List<String> streamUrls;

  // In order of preference: 720p is plenty on a phone; H.265 renditions are
  // skipped because not every device decodes them.
  static const _streamKeys = ['mp4_720p_mp4', 'mp4_hd_url', 'mp4_sd_url'];

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
      streamUrls: {
        for (final key in _streamKeys) ?readUrl(media?[key]),
        ..._playbackUrls(media?['playback_list']),
      }.map(_https).toList(),
    );
  }

  // Stream URLs come as plain http, which Android and iOS refuse by default;
  // the CDN serves the same signed URLs over https, as weibo.com uses them.
  static String _https(String url) =>
      url.startsWith('http://') ? 'https://${url.substring(7)}' : url;

  /// The plain mp4 entries of `playback_list`, best quality first; it also
  /// lists scrubbing thumbnails and may list adaptive streams.
  static Iterable<String> _playbackUrls(Object? list) {
    final entries = [
      for (final item in readMaps(list))
        if (readMap(item['play_info']) case final info?
            when info['mime'] == 'video/mp4' && info['protocol'] != 'dash')
          if (readUrl(info['url']) case final url?)
            (url, readInt(readMap(item['meta'])?['quality_index']) ?? 0),
    ]..sort((a, b) => b.$2.compareTo(a.$2));
    return entries.map((e) => e.$1);
  }
}

/// An item of a post mixing pictures and videos (`mix_media_info`).
sealed class MixedMedia {
  const MixedMedia();

  /// Items hold a `pic_infos` entry or a `page_info` as their `data`.
  static MixedMedia? tryFromJson(Map<String, Object?> json) {
    final data = readMap(json['data']);
    return switch (json['type']) {
      'pic' => switch (PicInfo.tryFromJson(data, id: readString(json['id']))) {
        final pic? => MixedPic(pic),
        null => null,
      },
      'video' => switch (VideoInfo.tryFromPageInfo(data)) {
        final video? => MixedVideo(video),
        null => null,
      },
      _ => null,
    };
  }
}

final class MixedPic extends MixedMedia {
  const MixedPic(this.pic);

  final PicInfo pic;
}

final class MixedVideo extends MixedMedia {
  const MixedVideo(this.video);

  final VideoInfo video;
}

/// A link preview such as a topic or live stream.
class PageCard {
  const PageCard({required this.title, this.description, this.imageUrl});

  final String title;
  final String? description;
  final String? imageUrl;

  /// Null when the card would only repeat the title of a link in the text,
  /// [urlStruct] describing those links: a `webpage` card can come with no
  /// picture or description (PLAN §8).
  static PageCard? tryFromPageInfo(
    Map<String, Object?>? pageInfo, {
    List<Map<String, Object?>> urlStruct = const [],
  }) {
    if (pageInfo == null || pageInfo['object_type'] == 'video') return null;
    final title = readString(pageInfo['page_title']);
    if (title == null) return null;
    final description =
        readString(pageInfo['page_desc']) ?? readString(pageInfo['content2']);
    final imageUrl = readUrl(pageInfo['page_pic']);
    if (description == null &&
        imageUrl == null &&
        urlStruct.any((u) => readString(u['url_title']) == title)) {
      return null;
    }
    return PageCard(title: title, description: description, imageUrl: imageUrl);
  }
}

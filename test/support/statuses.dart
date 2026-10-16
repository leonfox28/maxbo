// Hand-written, synthetic Weibo JSON shaped like the web API's responses.
// Never paste real responses here (see captured/ and PLAN §2).

Map<String, Object?> userJson({int id = 1001, String name = '示例用户'}) => {
  'id': id,
  'idstr': '$id',
  'screen_name': name,
  'avatar_large': 'https://tvax1.sinaimg.cn/crop.0.0.180.180.180/abc.jpg?KID=imgbed,tva&Expires=1&ssig=x',
  'verified': false,
  'verified_type': -1,
};

Map<String, Object?> picJson(
  String pid, {
  String type = 'pic',
  int width = 1200,
  int height = 900,
}) {
  Map<String, Object?> variant(String size, int w) => {
    'url': 'https://wx1.sinaimg.cn/$size/$pid.jpg',
    'width': w,
    'height': w * height ~/ width,
  };
  return {
    'pic_id': pid,
    'type': type,
    'thumbnail': variant('wap180', 180),
    'bmiddle': variant('wap360', 360),
    'large': variant('orj960', 960),
    'mw2000': variant('mw2000', width < 2000 ? width : 2000),
    'original': variant('orj1080', 1080),
    'largest': variant('large', width),
  };
}

Map<String, Object?> statusJson({
  int id = 5000000000000001,
  String text = '一条示例微博',
  Map<String, Object?>? user,
  List<Map<String, Object?>> pics = const [],
  Map<String, Object?>? retweeted,
  Map<String, Object?>? pageInfo,
  Map<String, Object?> extra = const {},
}) => {
  'id': id,
  'idstr': '$id',
  'mid': '$id',
  'mblogid': 'Abc$id',
  'created_at': 'Mon Oct 12 13:17:46 +0800 2026',
  'source': '示例客户端',
  'region_name': '发布于 上海',
  'text': text,
  'text_raw': text,
  'user': user ?? userJson(),
  'isLongText': false,
  'pic_num': pics.length,
  'pic_ids': [for (final p in pics) p['pic_id']],
  'pic_infos': {for (final p in pics) '${p['pic_id']}': p},
  'retweeted_status': ?retweeted,
  'page_info': ?pageInfo,
  'reposts_count': 3,
  'comments_count': 12345,
  'attitudes_count': 0,
  'attitudes_status': 0,
  'isAd': false,
  'readtimetype': 'mblog',
  ...extra,
};

/// A friendstimeline response holding [statuses].
Map<String, Object?> feedJson(
  List<Map<String, Object?>> statuses, {
  int maxId = 0,
}) => {
  'ok': 1,
  'statuses': statuses,
  'total_number': statuses.length,
  'since_id': statuses.isEmpty ? 0 : statuses.first['id'],
  'max_id': maxId,
  'since_id_str': statuses.isEmpty ? '0' : '${statuses.first['id']}',
  'max_id_str': '$maxId',
};

/// A video page_info whose streams are signed until [expires] (Unix seconds).
Map<String, Object?> videoPageInfo({int expires = 2000000000}) {
  String stream(String label) =>
      'http://f.video.weibocdn.com/o0/$label.mp4?label=$label&Expires=$expires&ssig=s&KID=unistore,video';
  return {
    'object_type': 'video',
    'page_pic': 'https://wx1.sinaimg.cn/orj480/cover.jpg',
    'media_info': {
      'duration': 49,
      'mp4_sd_url': stream('mp4_ld'),
      'mp4_hd_url': stream('mp4_hd'),
      'mp4_720p_mp4': stream('mp4_720p'),
      'h265_mp4_hd': stream('h265_hd'),
      'playback_list': [
        {
          'meta': {'label': 'scrubber_hd', 'type': 3},
          'play_info': {
            'mime': 'image/jpeg',
            'url': 'https://wx3.sinaimg.cn/large/scrub.jpg',
          },
        },
        {
          'meta': {'label': 'mp4_hd', 'quality_index': 480, 'type': 1},
          'play_info': {
            'mime': 'video/mp4',
            'protocol': 'general',
            'url': stream('list_480'),
          },
        },
        {
          'meta': {'label': 'mp4_1080p', 'quality_index': 1080, 'type': 1},
          'play_info': {
            'mime': 'video/mp4',
            'protocol': 'general',
            'url': stream('list_1080'),
          },
        },
      ],
    },
  };
}

Map<String, Object?> commentJson({
  int id = 6000000000000001,
  String text = '一条示例评论',
  Map<String, Object?>? user,
  int likes = 0,
  int replyCount = 0,
  List<Map<String, Object?>> replies = const [],
  List<Map<String, Object?>> urlStruct = const [],
}) => {
  'id': id,
  'idstr': '$id',
  'rootid': id,
  'created_at': 'Mon Oct 12 13:20:00 +0800 2026',
  'source': '来自上海',
  'text': text,
  // Like Weibo's, text_raw links the comment's own pictures.
  'text_raw': [
    text,
    for (final u in urlStruct)
      if (u['pic_infos'] != null) u['short_url'],
  ].join(' '),
  'pic_num': urlStruct.where((u) => u['pic_infos'] != null).length,
  'user': user ?? userJson(id: 2001, name: '评论者'),
  'like_counts': likes,
  'liked': false,
  'total_number': replyCount,
  'comments': replies,
  'url_struct': urlStruct,
};

/// A url_struct entry for a picture attached to a comment, which (unlike a
/// status picture) has no pic_id and no sizes.
Map<String, Object?> commentPicJson(String pid) => {
  'short_url': 'http://t.cn/$pid',
  'url_title': '查看图片',
  'url_type': 39,
  'pic_ids': [pid],
  'pic_infos': {
    pid: {
      'thumbnail': {'url': 'https://wx4.sinaimg.cn/thumbnail/$pid.jpg'},
      'bmiddle': {'url': 'https://wx4.sinaimg.cn/bmiddle/$pid.jpg'},
      'large': {'url': 'https://wx4.sinaimg.cn/large/$pid.jpg'},
      'woriginal': {'url': 'https://wx4.sinaimg.cn/woriginal/$pid.jpg'},
    },
  },
};

/// A buildComments response; [maxId] 0 marks the last page.
Map<String, Object?> commentsPageJson(
  List<Map<String, Object?>> comments, {
  int maxId = 0,
  int? total,
}) => {
  'ok': 1,
  'data': comments,
  'rootComment': [],
  'total_number': total ?? comments.length,
  'max_id': maxId,
  'trendsText': '',
};

/// A repostTimeline response.
Map<String, Object?> repostsPageJson(
  List<Map<String, Object?>> reposts, {
  int maxPage = 1,
  int? total,
}) => {
  'ok': 1,
  'data': reposts,
  'total_number': total ?? reposts.length,
  'max_page': maxPage,
};

/// The `data` of a profile/info response for [user] (see [userJson]).
Map<String, Object?> profileDataJson({
  Map<String, Object?>? user,
  Map<String, Object?> extra = const {},
}) => {
  'user': {
    ...user ?? userJson(),
    'description': '这是一段简介',
    'location': '上海 浦东新区',
    'gender': 'f',
    'followers_count': 123456,
    'followers_count_str': '12.3万',
    'friends_count': 321,
    'statuses_count': 4567,
    'following': true,
    'follow_me': false,
    'cover_image_phone': 'https://ww1.sinaimg.cn/crop.0.0.640.640.640/cover1.jpg;https://ww1.sinaimg.cn/crop.0.0.640.640.640/cover2.jpg',
    ...extra,
  },
  'tabList': [
    {'name': 'home', 'tabName': '精选'},
  ],
  'blockText': '',
};

/// A mymblog response; an empty [sinceId] marks the last page.
Map<String, Object?> userStatusesJson(
  List<Map<String, Object?>> statuses, {
  String sinceId = '5100000000000000kp2',
  int total = 100,
}) => {
  'ok': 1,
  'data': {
    'since_id': sinceId,
    'list': statuses,
    'status_visible': 0,
    'bottom_tips_visible': false,
    'bottom_tips_text': '',
    'topicList': [],
    'total': total,
  },
};

/// A side/hotSearch entry. Ranked entries have [realpos]; entries Weibo
/// inserts don't.
Map<String, Object?> hotItemJson(
  String word, {
  int? realpos,
  int num = 1234567,
  String? scheme,
  String? icon,
  String? smallIcon,
  String color = '#ff9406',
  int? isAd,
}) => {
  'word': word,
  'note': word,
  'word_scheme': scheme ?? '#$word#',
  'num': num,
  'rank': (realpos ?? 1) - 1,
  'realpos': ?realpos,
  'label_name': icon ?? '',
  'icon_desc': ?icon,
  'icon_desc_color': ?(icon == null ? null : color),
  'small_icon_desc': smallIcon ?? icon ?? '',
  'small_icon_desc_color': (smallIcon ?? icon) == null ? '' : color,
  'flag': 1,
  'is_ad': ?isAd,
};

/// A side/hotSearch response.
Map<String, Object?> hotSearchJson({
  List<Map<String, Object?>> realtime = const [],
  List<Map<String, Object?>> hotgovs = const [],
  Map<String, Object?>? hotgov,
}) => {
  'ok': 1,
  'data': {'realtime': realtime, 'hotgovs': hotgovs, 'hotgov': ?hotgov},
};

/// One of the 消息 lists (message/cmt, comments/mentions, statuses/mentions,
/// message/attitudes): items under `data.<key>`; [nextCursor] 0 marks the
/// last page.
Map<String, Object?> noticePageJson(
  String key,
  List<Map<String, Object?>> items, {
  int nextCursor = 0,
}) => {
  'ok': 1,
  'data': {
    key: items,
    'next_cursor': nextCursor,
    'previous_cursor': 0,
    'total_number': items.length,
    'miss_ids': [],
  },
};

/// A comment as the notification lists have it, with the post it is on.
Map<String, Object?> commentNoticeJson({
  int id = 6000000000000001,
  String text = '一条评论通知',
  Map<String, Object?>? status,
  Map<String, Object?>? replyTo,
}) => {
  ...commentJson(id: id, text: text),
  'status': status ?? statusJson(text: '被评论的微博'),
  'reply_comment': ?replyTo,
};

/// A like as message/attitudes has it. With [cut], the post is the cut-down
/// form Weibo sometimes sends, without pictures or counts.
Map<String, Object?> likeNoticeJson({
  int id = 7000000000000001,
  Map<String, Object?>? status,
  Map<String, Object?>? comment,
  bool cut = false,
}) {
  final post = status ?? statusJson(text: '被赞的微博');
  return {
    'id': id,
    'idStr': '$id',
    'created_at': 'Mon Oct 12 14:00:00 +0800 2026',
    'attitude': 'heart',
    'user': userJson(id: 3001, name: '点赞的人'),
    'status': ?(cut
        ? {
            for (final k in ['id', 'idstr', 'mid', 'mblogid', 'text', 'user'])
              k: post[k],
          }
        : comment == null
        ? post
        : null),
    'comment': ?comment,
  };
}

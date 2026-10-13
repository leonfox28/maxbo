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
  'text_raw': text,
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

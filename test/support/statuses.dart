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

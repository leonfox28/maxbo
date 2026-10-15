import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/feed_page.dart';
import 'package:maxbo/data/models/pic_info.dart';
import 'package:maxbo/data/models/status.dart';
import 'package:maxbo/widgets/rich_text/status_text_parser.dart';

import '../../support/statuses.dart';

void main() {
  group('Status', () {
    test('parses a plain status, keeping ids as strings', () {
      final s = Status.tryFromJson(statusJson(id: 5000000000000042))!;

      expect(s.id, '5000000000000042');
      expect(s.mblogId, 'Abc5000000000000042');
      expect(s.user?.id, '1001');
      expect(s.user?.screenName, '示例用户');
      expect(s.user?.verifiedType, isNull);
      expect(s.createdAt, DateTime.utc(2026, 10, 12, 5, 17, 46));
      expect(s.source, '示例客户端');
      expect(s.region, '发布于 上海');
      expect(s.commentsCount, 12345);
      expect(s.liked, isFalse);
      expect(s.isAd, isFalse);
      expect(s.pics, isEmpty);
      expect(s.retweeted, isNull);
    });

    test('tolerates missing and mistyped fields', () {
      expect(Status.tryFromJson(null), isNull);
      expect(Status.tryFromJson({'text': 'no id'}), isNull);

      final s = Status.tryFromJson({
        'idstr': '7',
        'user': 'not a map',
        'pic_infos': [],
        'reposts_count': '12',
        'attitudes_count': null,
        'created_at': 'yesterday',
        'source': '',
      })!;
      expect(s.user, isNull);
      expect(s.pics, isEmpty);
      expect(s.repostsCount, 12);
      expect(s.likesCount, 0);
      expect(s.createdAt, isNull);
      expect(s.source, isNull);
      expect(s.text, '');
    });

    test('strips HTML from source', () {
      final s = Status.tryFromJson(
        statusJson(
          extra: {
            'source':
                '<a target="_blank" href="https://x.example/">某某&amp;客户端</a>',
          },
        ),
      )!;
      expect(s.source, '某某&客户端');
    });

    test('orders pictures by pic_ids and reads their types', () {
      final s = Status.tryFromJson(
        statusJson(
          pics: [
            picJson('b'),
            picJson('a', type: 'livephoto'),
          ],
          extra: {
            'pic_ids': ['a', 'b', 'missing'],
            'pic_num': 12,
          },
        ),
      )!;
      expect([for (final p in s.pics) p.id], ['a', 'b']);
      expect(s.pics.first.type, PicType.livePhoto);
      expect(s.picCount, 12);
    });

    test('labels ads by any known marker, and never drops them', () {
      bool isAd(Map<String, Object?> extra) =>
          Status.tryFromJson(statusJson(extra: extra))!.isAd;
      expect(isAd({'isAd': true}), isTrue);
      expect(isAd({'readtimetype': 'adMblog'}), isTrue);
      expect(isAd({'mark': '1_reallog_mark_ad:123|abc'}), isTrue);
      expect(isAd({'mark': 'something_else'}), isFalse);
      // Content Weibo classed as advertising, not an ad placed in the feed.
      expect(
        isAd({
          'isAd': true,
          'readtimetype': 'adMblog',
          'mark': '999_reallog_mark_ad:999|WeiboADNatural',
        }),
        isFalse,
      );

      final page = FeedPage.fromJson(
        feedJson([
          statusJson(id: 1),
          statusJson(id: 2, extra: {'isAd': true}),
        ]),
      );
      expect(page.statuses.map((s) => s.isAd), [false, true]);
    });

    test('reads verified users and liked state', () {
      final s = Status.tryFromJson(
        statusJson(
          user: {...userJson(), 'verified': true, 'verified_type': 0},
          extra: {'attitudes_status': 1},
        ),
      )!;
      expect(s.user?.verifiedType, 0);
      expect(s.liked, isTrue);
    });

    test('parses a retweet whose source was deleted', () {
      final s = Status.tryFromJson(
        statusJson(
          text: '转发',
          retweeted: {'idstr': '9', 'text': '抱歉，此微博已被作者删除。', 'user': null},
        ),
      )!;
      expect(s.retweeted?.id, '9');
      expect(s.retweeted?.user, isNull);
      expect(s.retweeted?.text, '抱歉，此微博已被作者删除。');
    });

    test('falls back to text_raw for a retweet source in mentions', () {
      Status? retweetOf(Map<String, Object?> source) =>
          Status.tryFromJson(statusJson(text: '转发', retweeted: source))!
              .retweeted;
      final stub = {
        ...statusJson(id: 9, text: '', extra: {'text_raw': 'a<b>\n第二行'}),
      }..remove('user');

      final short = retweetOf(stub)!;
      expect(short.user, isNull);
      expect(short.text, 'a&lt;b&gt;\n第二行');
      expect(plainStatusText(short.text), 'a<b> 第二行');

      final long = retweetOf({...stub, 'isLongText': true})!;
      expect(parseStatusText(long.text).last, isA<ExpandSegment>());
    });

    test('links up text_raw as text would, titling links from the '
        'retweeting post', () {
      final s = Status.tryFromJson(
        statusJson(
          text: '转发',
          retweeted: {
            ...statusJson(
              id: 9,
              text: '',
              extra: {'text_raw': '看http://t.cn/A1b2正文，问a@b.cn和@某人 #话题#'},
            ),
            'url_struct': null,
          },
          extra: {
            'url_struct': [
              {
                'short_url': 'http://t.cn/A1b2',
                'long_url': 'https://weibo.com/1/Abc9',
                'url_title': '微博正文',
              },
            ],
          },
        ),
      )!;
      final source = s.retweeted!;

      final segments = parseStatusText(source.text, urls: source.urls);
      expect(segments.map((s) => s is LinkSegment ? s.link : null).nonNulls, [
        isA<WebLink>().having(
          (l) => '${l.url}',
          'url',
          'https://weibo.com/1/Abc9',
        ),
        isA<MentionLink>().having((l) => l.screenName, 'name', '某人'),
        isA<TopicLink>().having((l) => l.topic, 'topic', '话题'),
      ]);
      expect(plainStatusText(source.text), '看微博正文正文，问a@b.cn和@某人 #话题#');
    });

    test('reads a video cover, length and shape', () {
      final s = Status.tryFromJson(
        statusJson(
          pageInfo: {
            'object_type': 'video',
            'page_pic': 'https://wx1.sinaimg.cn/orj480/cover.jpg',
            'media_info': {
              'duration': 95,
              'big_pic_info': {
                'pic_big': {
                  'url': '//wx1.sinaimg.cn/big.jpg',
                  'width': 1280,
                  'height': 720,
                },
              },
            },
          },
        ),
      )!;
      expect(s.video?.coverUrl, 'https://wx1.sinaimg.cn/orj480/cover.jpg');
      expect(s.video?.duration, 95);
      expect(s.video?.aspectRatio, closeTo(16 / 9, 0.001));
      expect(s.pageCard, isNull);
    });

    test('lists playable streams over https, preferred first', () {
      final s = Status.tryFromJson(statusJson(pageInfo: videoPageInfo()))!;
      final labels = [
        for (final url in s.video!.streamUrls) Uri.parse(url).pathSegments.last,
      ];
      // 720p first, then the hd/sd keys, then the best of playback_list;
      // H.265 and the scrubbing thumbnails are left out.
      expect(labels, [
        'mp4_720p.mp4',
        'mp4_hd.mp4',
        'mp4_ld.mp4',
        'list_1080.mp4',
        'list_480.mp4',
      ]);
      expect(s.video!.streamUrls, everyElement(startsWith('https://')));
    });

    test('reads other page_info as a link card', () {
      final s = Status.tryFromJson(
        statusJson(
          pageInfo: {
            'object_type': 'topic',
            'page_title': '#示例话题#',
            'page_desc': '一段描述',
            'page_pic': '//wx2.sinaimg.cn/large/topic.jpg',
          },
        ),
      )!;
      expect(s.video, isNull);
      expect(s.pageCard?.title, '#示例话题#');
      expect(s.pageCard?.description, '一段描述');
      expect(s.pageCard?.imageUrl, 'https://wx2.sinaimg.cn/large/topic.jpg');
    });

    test('reads whether it is pinned to its profile', () {
      final pinned = Status.tryFromJson(
        statusJson(extra: {'isTop': 1, 'mblogtype': 2}),
      )!;
      expect(pinned.pinned, isTrue);
      expect(Status.tryFromJson(statusJson())!.pinned, isFalse);
    });
  });

  group('PicInfo', () {
    test('picks the smallest variant that covers the size', () {
      final pic = PicInfo.tryFromJson(picJson('p', width: 1200, height: 900))!;
      // Shorter sides: 135, 270, 720, 900 (largest/original are excluded).
      expect(pic.variantFor(100).url, contains('/wap180/'));
      expect(pic.variantFor(300).url, contains('/orj960/'));
      expect(pic.variantFor(5000).url, contains('/mw2000/'));
      expect(pic.largest?.url, contains('/large/'));
      expect(pic.aspectRatio, closeTo(4 / 3, 0.001));
      expect(pic.isLong, isFalse);
    });

    test('flags long pictures', () {
      final pic = PicInfo.tryFromJson(picJson('p', width: 600, height: 4000))!;
      expect(pic.isLong, isTrue);
    });

    test('needs an id and at least one usable variant', () {
      expect(PicInfo.tryFromJson({'pic_id': 'p'}), isNull);
      expect(
        PicInfo.tryFromJson({
          'thumbnail': {'url': 'https://wx1.sinaimg.cn/a.jpg'},
        }),
        isNull,
      );
    });
  });

  group('PicInfo viewing', () {
    test('views the largest list rendition, or the whole file for a GIF', () {
      final pic = PicInfo.tryFromJson(picJson('p', width: 3000, height: 2000))!;
      expect(pic.viewerVariant.url, contains('/mw2000/'));

      final gif = PicInfo.tryFromJson(picJson('g', type: 'gif'))!;
      expect(gif.viewerVariant.url, contains('/large/'));
    });

    test('wraps a linked picture URL', () {
      final pic = PicInfo.fromUrl('https://wx1.sinaimg.cn/large/p1.gif');
      expect(pic.type, PicType.gif);
      expect(pic.viewerVariant.url, 'https://wx1.sinaimg.cn/large/p1.gif');
    });

    test('picsOf follows pic_ids and names pictures by their key', () {
      final pics = picsOf({
        'pic_ids': ['b', 'a'],
        'pic_infos': {
          'a': picJson('a'),
          'b': {
            'thumbnail': {'url': 'https://wx1.sinaimg.cn/thumbnail/b.jpg'},
          },
        },
      });
      expect(pics.map((p) => p.id), ['b', 'a']);
    });
  });

  group('FeedPage', () {
    test('continues from max_id_str', () {
      final page = FeedPage.fromJson({
        ...feedJson([statusJson()]),
        'max_id': 1,
        'max_id_str': '5000000000000000123',
      });
      expect(page.nextCursor, '5000000000000000123');
    });

    test('ends at max_id 0 or an empty page', () {
      expect(FeedPage.fromJson(feedJson([statusJson()])).nextCursor, isNull);
      expect(FeedPage.fromJson(feedJson([], maxId: 99)).nextCursor, isNull);
    });

    test('skips statuses it cannot read', () {
      final page = FeedPage.fromJson({
        'statuses': [
          statusJson(id: 1),
          'junk',
          {'text': 'no id'},
        ],
      });
      expect(page.statuses.map((s) => s.id), ['1']);
    });
  });
}

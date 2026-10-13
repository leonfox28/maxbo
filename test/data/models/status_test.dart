import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/feed_page.dart';
import 'package:maxbo/data/models/pic_info.dart';
import 'package:maxbo/data/models/status.dart';

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

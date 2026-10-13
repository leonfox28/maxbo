import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/hot_search.dart';

import '../../support/statuses.dart';

void main() {
  HotSearch parse(Map<String, Object?> json) =>
      HotSearch.fromJson(json['data']! as Map<String, Object?>);

  test('reads ranked entries', () {
    final hot = parse(
      hotSearchJson(
        realtime: [
          hotItemJson('第一条', realpos: 1, icon: '热', num: 1587784),
          hotItemJson('第二条', realpos: 2, scheme: '第二条'),
        ],
      ),
    );

    final [first, second] = hot.items;
    expect(first.word, '第一条');
    expect(first.query, '#第一条#');
    expect(first.rank, 1);
    expect(first.heat, 1587784);
    expect(first.badge, '热');
    expect(first.badgeColor, 0xFFFF9406);
    expect(first.isAd, isFalse);
    // Not every entry is a topic.
    expect(second.query, '第二条');
    expect(second.rank, 2);
    expect(second.badge, isNull);
  });

  test('keeps inserted and paid entries in place, labelled', () {
    final hot = parse(
      hotSearchJson(
        realtime: [
          hotItemJson('第一条', realpos: 1),
          hotItemJson(
            '付费的一条',
            icon: '首发',
            smallIcon: '商',
            color: '#00b7ee',
            isAd: 1,
          ),
          hotItemJson('辟谣的一条', icon: '辟谣', smallIcon: '', isAd: 1),
          hotItemJson('没有标签的一条', isAd: 1),
          hotItemJson('第二条', realpos: 2),
        ],
      ),
    );

    expect(hot.items.map((i) => i.word), [
      '第一条',
      '付费的一条',
      '辟谣的一条',
      '没有标签的一条',
      '第二条',
    ]);
    final [_, paid, rebuttal, unlabelled, _] = hot.items;
    expect(paid.rank, isNull);
    expect(paid.isAd, isTrue);
    // The short label is the one saying 商.
    expect(paid.badge, '商');
    expect(paid.badgeColor, 0xFF00B7EE);
    expect(rebuttal.badge, '辟谣');
    expect(unlabelled.badge, '荐');
  });

  test('reads the pinned entries without their #', () {
    final hot = parse(
      hotSearchJson(
        hotgovs: [
          {'word': '#置顶的话题#', 'name': '#置顶的话题#', 'icon_desc': '热'},
        ],
        hotgov: {'word': '#另一个#', 'note': '#另一个#'},
      ),
    );

    final pinned = hot.pinned.single;
    expect(pinned.word, '置顶的话题');
    expect(pinned.query, '#置顶的话题#');
    expect(pinned.rank, isNull);
    expect(pinned.heat, isNull);
    expect(pinned.badge, '热');
  });

  test('falls back to the single pinned entry', () {
    final hot = parse(hotSearchJson(hotgov: {'word': '#置顶的话题#'}));

    expect(hot.pinned.single.word, '置顶的话题');
  });

  test('tolerates missing lists and bad colours', () {
    final hot = HotSearch.fromJson({
      'realtime': [
        {'word': '一条', 'small_icon_desc': '新', 'small_icon_desc_color': 'red'},
        {'num': 5},
      ],
    });

    expect(hot.pinned, isEmpty);
    expect(hot.items.single.badge, '新');
    expect(hot.items.single.badgeColor, isNull);
  });
}

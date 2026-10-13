import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/status.dart';
import 'package:maxbo/widgets/pic_grid/pic_grid.dart';
import 'package:maxbo/widgets/rich_text/status_text_parser.dart';
import 'package:maxbo/widgets/status_card/status_card.dart';

import '../support/statuses.dart';

void main() {
  Future<void> pumpCard(
    WidgetTester tester,
    Map<String, Object?> json, {
    StatusCardActions actions = const StatusCardActions(),
  }) => tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          child: StatusCard(Status.tryFromJson(json)!, actions: actions),
        ),
      ),
    ),
  );

  testWidgets('shows author, text, meta and counts', (tester) async {
    await pumpCard(tester, statusJson(text: '你好，世界'));

    expect(find.text('示例用户'), findsOneWidget);
    expect(find.text('你好，世界'), findsOneWidget);
    expect(find.textContaining('来自 示例客户端'), findsOneWidget);
    expect(find.text('3'), findsOneWidget); // reposts
    expect(find.text('1.2万'), findsOneWidget); // comments
    expect(find.text('赞'), findsOneWidget); // no likes yet
    expect(find.text('广告'), findsNothing);
  });

  testWidgets('labels ads', (tester) async {
    await pumpCard(tester, statusJson(extra: {'isAd': true}));
    expect(find.text('广告'), findsOneWidget);
  });

  testWidgets('shows the retweeted post with its author', (tester) async {
    await pumpCard(
      tester,
      statusJson(
        text: '转发理由',
        retweeted: statusJson(
          id: 2,
          text: '原文内容',
          user: userJson(id: 2002, name: '原作者'),
        ),
      ),
    );

    expect(find.text('转发理由'), findsOneWidget);
    expect(find.text('@原作者：原文内容'), findsOneWidget);
  });

  testWidgets('shows nine pictures at most, with the rest counted', (
    tester,
  ) async {
    await pumpCard(
      tester,
      statusJson(pics: [for (var i = 0; i < 12; i++) picJson('p$i')]),
    );

    expect(
      find.descendant(
        of: find.byType(PicGrid),
        matching: find.byType(ClipRRect),
      ),
      findsNWidgets(9),
    );
    expect(find.text('+3'), findsOneWidget);
  });

  testWidgets('shows a video cover with its length', (tester) async {
    var tapped = false;
    await pumpCard(
      tester,
      statusJson(
        pageInfo: {
          'object_type': 'video',
          'page_pic': 'https://wx1.sinaimg.cn/orj480/cover.jpg',
          'media_info': {'duration': 95},
        },
      ),
      actions: StatusCardActions(onVideo: (_) => tapped = true),
    );

    expect(find.text('1:35'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.play_circle_fill));
    expect(tapped, isTrue);
  });

  testWidgets('a retweet shows its source video once', (tester) async {
    // Weibo copies the source's page_info onto the retweet as well.
    const video = {
      'object_type': 'video',
      'page_pic': 'https://wx1.sinaimg.cn/orj480/cover.jpg',
      'media_info': {'duration': 60},
    };
    await pumpCard(
      tester,
      statusJson(
        text: '转发',
        pageInfo: video,
        retweeted: statusJson(id: 2, text: '原视频', pageInfo: video),
      ),
    );

    expect(find.text('1:00'), findsOneWidget);
  });

  testWidgets('links in the text report what was tapped', (tester) async {
    final tapped = <StatusLink>[];
    await pumpCard(
      tester,
      statusJson(
        text:
            '看<a href=/n/朋友 usercard="name=朋友">@朋友</a>的'
            '<a href="//s.weibo.com/weibo?q=%23话题%23">#话题#</a>',
      ),
      actions: StatusCardActions(onLink: tapped.add),
    );

    await tester.tapOnText(find.textRange.ofSubstring('@朋友'));
    await tester.tapOnText(find.textRange.ofSubstring('#话题#'));

    expect(tapped, [
      isA<MentionLink>().having((l) => l.screenName, 'name', '朋友'),
      isA<TopicLink>().having((l) => l.topic, 'topic', '话题'),
    ]);
  });
}

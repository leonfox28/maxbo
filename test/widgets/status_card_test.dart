import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/data/models/pic_info.dart';
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
    bool detail = false,
  }) => tester.pumpWidget(
    ProviderScope(
      retry: noRetry,
      child: MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: StatusCard(
              Status.tryFromJson(json)!,
              actions: actions,
              detail: detail,
            ),
          ),
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

  testWidgets('labels posts pinned to a profile', (tester) async {
    await pumpCard(tester, statusJson(extra: {'isTop': 1, 'isAd': true}));
    expect(find.text('置顶'), findsOneWidget);
    expect(find.text('广告'), findsOneWidget);
  });

  testWidgets('the avatar and the name report the author', (tester) async {
    final tapped = <String>[];
    await pumpCard(
      tester,
      statusJson(user: userJson(id: 1001, name: '作者')),
      actions: StatusCardActions(onUser: (u) => tapped.add(u.id)),
    );

    await tester.tap(find.byType(ClipOval));
    await tester.tap(find.text('作者'));
    expect(tapped, ['1001', '1001']);
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
      actions: StatusCardActions(onVideo: (_, _) => tapped = true),
    );

    expect(find.text('1:35'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.play_circle_fill));
    expect(tapped, isTrue);
  });

  testWidgets('shows the pictures and videos of a retweeted mixed post', (
    tester,
  ) async {
    (Status, int?)? video;
    (List<PicInfo>, int)? picture;
    await pumpCard(
      tester,
      statusJson(
        text: '转发',
        retweeted: statusJson(
          id: 2,
          text: '原微博',
          extra: mixedMediaFields([
            videoPageInfo(name: 'first'),
            picJson('p1'),
            videoPageInfo(name: 'second'),
          ]),
        ),
      ),
      actions: StatusCardActions(
        onVideo: (status, index) => video = (status, index),
        onPicture: (pics, index) => picture = (pics, index),
      ),
    );

    expect(find.text('0:49'), findsNWidgets(2));
    await tester.tap(find.byIcon(Icons.play_circle_fill).last);
    expect(video?.$1.id, '2');
    expect(video?.$2, 1);

    await tester.tap(
      find
          .descendant(
            of: find.byType(PicGrid),
            matching: find.byType(ClipRRect),
          )
          .at(1),
    );
    expect(picture?.$1.map((p) => p.id), ['p1']);
    expect(picture?.$2, 0);
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

  testWidgets('tapping the card or its retweet opens that post', (
    tester,
  ) async {
    final opened = <String>[];
    await pumpCard(
      tester,
      statusJson(
        id: 1,
        text: '转发理由',
        retweeted: statusJson(id: 2, text: '原文内容'),
      ),
      actions: StatusCardActions(onOpen: (s) => opened.add(s.id)),
    );

    await tester.tap(find.text('转发理由'));
    // Clear of the author's @mention, which is a link of its own.
    await tester.tapOnText(find.textRange.ofSubstring('原文内容'));

    expect(opened, ['1', '2']);
  });

  testWidgets('a deleted retweet does not open', (tester) async {
    final opened = <String>[];
    await pumpCard(
      tester,
      statusJson(
        id: 1,
        text: '转发理由',
        retweeted: {'idstr': '9', 'text': '抱歉，此微博已被作者删除。'},
      ),
      actions: StatusCardActions(onOpen: (s) => opened.add(s.id)),
    );

    await tester.tap(find.text('抱歉，此微博已被作者删除。'));

    expect(opened, ['1']); // The tap falls through to the outer card.
  });

  testWidgets('a retweet source without its author still opens', (
    tester,
  ) async {
    final opened = <String>[];
    // As in `statuses/mentions`: no `user`, the text only in `text_raw`.
    final source = {
      ...statusJson(id: 9, text: '', extra: {'text_raw': '原文开头'}),
      'isLongText': true,
    }..remove('user');
    await pumpCard(
      tester,
      statusJson(id: 1, text: '转发理由', retweeted: source),
      actions: StatusCardActions(
        onOpen: (s) => opened.add(s.id),
        onExpand: (s) => opened.add('expand ${s.id}'),
      ),
    );

    await tester.tapOnText(find.textRange.ofSubstring('原文开头'));
    await tester.tapOnText(find.textRange.ofSubstring('展开'));

    expect(opened, ['9', 'expand 9']);
  });

  testWidgets('a mentions retweet source shows the author from its card', (
    tester,
  ) async {
    final source = {
      ...statusJson(id: 9, text: '', extra: {'text_raw': '原文开头'}),
    }..remove('user');
    await pumpCard(
      tester,
      statusJson(
        id: 1,
        text: '转发理由',
        retweeted: source,
        pageInfo: {
          'object_type': 'meaasgeObjType',
          'content1': '@原作者',
          'uidPageInfo': '42',
          'mblogid': 'Abc9',
        },
      ),
    );

    expect(find.textContaining('@原作者'), findsOneWidget);
    expect(find.textContaining('原文开头'), findsOneWidget);
  });

  testWidgets('in detail mode the card is inert and has no counts', (
    tester,
  ) async {
    final opened = <String>[];
    await pumpCard(
      tester,
      statusJson(text: '正文'),
      actions: StatusCardActions(onOpen: (s) => opened.add(s.id)),
      detail: true,
    );

    await tester.tap(find.text('正文'));

    expect(opened, isEmpty);
    expect(find.text('1.2万'), findsNothing);
  });

  group('on a wide card', () {
    void widen(WidgetTester tester, {double width = 1000}) {
      tester.view.physicalSize = Size(width, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
    }

    testWidgets('pictures sit beside the text, counts under it', (
      tester,
    ) async {
      widen(tester);
      await pumpCard(
        tester,
        statusJson(text: '配图的微博', pics: [picJson('a'), picJson('b')]),
      );

      final text = tester.getRect(find.text('配图的微博'));
      final grid = tester.getRect(find.byType(PicGrid));
      final likes = tester.getRect(find.text('赞'));
      expect(grid.left, greaterThan(text.left + 500));
      expect(grid.top, lessThan(text.bottom));
      expect(likes.right, lessThan(grid.left));
      expect(likes.top, greaterThan(grid.bottom));
    });

    testWidgets('so on an unfolded foldable, with less to the pictures', (
      tester,
    ) async {
      widen(tester, width: 850);
      await pumpCard(
        tester,
        statusJson(text: '很长的文字' * 60, pics: [picJson('a'), picJson('b')]),
      );

      final text = tester.getRect(find.textContaining('很长的文字'));
      final grid = tester.getRect(find.byType(PicGrid));
      expect(grid.top, lessThan(text.bottom));
      expect(text.width, greaterThan(400));
      expect(grid.width, lessThan(400));
    });

    testWidgets('the retweeted post sits beside the retweet', (tester) async {
      widen(tester);
      await pumpCard(
        tester,
        statusJson(
          text: '转发理由',
          retweeted: statusJson(id: 2, text: '原文内容'),
        ),
      );

      final reason = tester.getRect(find.text('转发理由'));
      final source = tester.getRect(find.textContaining('原文内容'));
      expect(source.left, greaterThan(reason.left + 500));
      expect(source.top, lessThan(reason.bottom));
    });

    testWidgets('text alone keeps to the text side', (tester) async {
      widen(tester);
      await pumpCard(tester, statusJson(text: '很长的文字' * 60));

      final text = tester.getRect(find.textContaining('很长的文字'));
      expect(text.right, lessThanOrEqualTo(1000 - 16 - 24 - 420));
    });

    testWidgets('not on the detail page', (tester) async {
      widen(tester);
      await pumpCard(
        tester,
        statusJson(text: '配图的微博', pics: [picJson('a'), picJson('b')]),
        detail: true,
      );

      final text = tester.getRect(find.text('配图的微博'));
      expect(
        tester.getRect(find.byType(PicGrid)).top,
        greaterThan(text.bottom),
      );
    });
  });

  testWidgets('tapping a picture reports the pictures and index', (
    tester,
  ) async {
    (List<PicInfo>, int)? tapped;
    await pumpCard(
      tester,
      statusJson(pics: [picJson('a'), picJson('b'), picJson('c')]),
      actions: StatusCardActions(onPicture: (pics, i) => tapped = (pics, i)),
    );

    await tester.tap(
      find
          .descendant(
            of: find.byType(PicGrid),
            matching: find.byType(ClipRRect),
          )
          .at(1),
    );

    expect(tapped?.$1.map((p) => p.id), ['a', 'b', 'c']);
    expect(tapped?.$2, 1);
  });
}

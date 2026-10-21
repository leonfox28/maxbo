import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/data/models/comment.dart';
import 'package:maxbo/data/models/pic_info.dart';
import 'package:maxbo/features/comments/comment_tile.dart';
import 'package:maxbo/widgets/status_card/status_card.dart';

import '../../support/ink.dart';
import '../../support/statuses.dart';

void main() {
  Future<void> pumpTile(
    WidgetTester tester,
    Map<String, Object?> json, {
    String? authorId,
    StatusCardActions actions = const StatusCardActions(),
    VoidCallback? onThread,
    VoidCallback? onTap,
  }) => tester.pumpWidget(
    ProviderScope(
      retry: noRetry,
      child: MaterialApp(
        home: Scaffold(
          body: CommentTile(
            Comment.tryFromJson(json)!,
            authorId: authorId,
            actions: actions,
            onThread: onThread,
            onTap: onTap,
          ),
        ),
      ),
    ),
  );

  testWidgets('shows the author, text, region and likes', (tester) async {
    await pumpTile(tester, commentJson(text: '说得好', likes: 12345));

    expect(find.text('评论者'), findsOneWidget);
    expect(find.text('说得好'), findsOneWidget);
    expect(find.textContaining('来自上海'), findsOneWidget);
    expect(find.text('1.2万'), findsOneWidget);
    expect(find.text('博主'), findsNothing);
  });

  testWidgets('pressing the like button shows no ink, its own or the '
      'comment\'s', (tester) async {
    await pumpTile(tester, commentJson(), onTap: () {});

    expect(
      await inkWhilePressing(tester, find.byIcon(Icons.thumb_up_outlined)),
      isEmpty,
    );
    // Pressing the comment itself still shows.
    expect(await inkWhilePressing(tester, find.text('一条示例评论')), isNotEmpty);
  });

  testWidgets('labels comments by the status author', (tester) async {
    await pumpTile(
      tester,
      commentJson(user: userJson(id: 1001, name: '博主本人')),
      authorId: '1001',
    );

    expect(find.text('博主'), findsOneWidget);
  });

  testWidgets('previews the thread and opens it', (tester) async {
    var opened = 0;
    await pumpTile(
      tester,
      commentJson(
        replyCount: 5,
        replies: [
          commentJson(
            id: 2,
            text: '第一条回复',
            user: userJson(id: 2002, name: '回复者'),
          ),
        ],
      ),
      onThread: () => opened++,
    );

    expect(find.text('@回复者：第一条回复'), findsOneWidget);
    await tester.tap(find.text('共5条回复 >'));
    expect(opened, 1);
  });

  testWidgets('without onThread there is no preview', (tester) async {
    await pumpTile(tester, commentJson(replyCount: 5));

    expect(find.text('共5条回复 >'), findsNothing);
  });

  testWidgets('tapping a picture opens the comment pictures', (tester) async {
    List<PicInfo>? tapped;
    await pumpTile(
      tester,
      commentJson(urlStruct: [commentPicJson('p1')]),
      actions: StatusCardActions(onPicture: (pics, _) => tapped = pics),
    );

    await tester.tap(find.byType(ClipRRect));

    expect(tapped?.single.id, 'p1');
  });

  testWidgets('leaves out the text link to a picture shown below', (
    tester,
  ) async {
    // Like Weibo's, a picture reply also links its picture from text.
    final reply = commentJson(
      id: 2,
      text:
          '图片回复 <a target=_blank data-pid=p2 '
          'href=https://wx3.sinaimg.cn/large/p2.jpg>查看图片</a>',
      urlStruct: [commentPicJson('p2')],
    );
    await pumpTile(tester, reply);

    expect(find.text('图片回复 '), findsOneWidget);
    expect(find.textContaining('查看图片'), findsNothing);
    expect(find.byType(ClipRRect), findsOneWidget);

    // The thread preview shows no pictures, so it keeps the link.
    await pumpTile(
      tester,
      commentJson(replyCount: 1, replies: [reply]),
      onThread: () {},
    );

    expect(find.textContaining('查看图片'), findsOneWidget);
  });
}

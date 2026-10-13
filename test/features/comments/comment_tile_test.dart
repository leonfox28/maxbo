import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/comment.dart';
import 'package:maxbo/data/models/pic_info.dart';
import 'package:maxbo/features/comments/comment_tile.dart';
import 'package:maxbo/widgets/status_card/status_card.dart';

import '../../support/statuses.dart';

void main() {
  Future<void> pumpTile(
    WidgetTester tester,
    Map<String, Object?> json, {
    String? authorId,
    StatusCardActions actions = const StatusCardActions(),
    VoidCallback? onThread,
  }) => tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: CommentTile(
          Comment.tryFromJson(json)!,
          authorId: authorId,
          actions: actions,
          onThread: onThread,
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
}

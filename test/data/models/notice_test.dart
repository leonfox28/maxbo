import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/notice.dart';

import '../../support/statuses.dart';

void main() {
  group('UnreadNotices', () {
    test('marks the kinds with counts, missing ones being 0', () {
      final unread = UnreadNotices.fromJson({
        'cmt': 2,
        'all_mention_status': 1,
        'mention_cmt': 0,
        'messages': 3,
      });

      expect(unread.kinds, {NoticeKind.comments, NoticeKind.mentionStatuses});
      expect(unread.any, isTrue);
      expect(unread.without(NoticeKind.comments).kinds, {
        NoticeKind.mentionStatuses,
      });
    });

    test('nothing counted is nothing unread', () {
      // Direct messages and the timeline aren't notifications here.
      final unread = UnreadNotices.fromJson({
        'dm_unread': 4,
        'status_24unread': 100,
        'remind_settings': {'msgbox': 1},
      });

      expect(unread.any, isFalse);
    });
  });

  test('CommentNotice carries the post and the comment it answers', () {
    final notice = CommentNotice.tryFromJson(
      commentNoticeJson(
        id: 42,
        text: '回复的内容',
        replyTo: commentJson(id: 41, text: '我的评论'),
      ),
    )!;

    expect(notice.id, '42');
    expect(notice.comment.text, '回复的内容');
    expect(notice.status?.text, '被评论的微博');
    expect(notice.replyTo?.text, '我的评论');
    expect(CommentNotice.tryFromJson({'text': '没有 id'}), isNull);
  });

  group('LikeNotice', () {
    test('a like of a post, by its idStr', () {
      final like = LikeNotice.tryFromJson(likeNoticeJson(id: 9))!;

      expect(like.id, '9');
      expect(like.user?.screenName, '点赞的人');
      expect(like.createdAt, isNotNull);
      expect(like.status?.text, '被赞的微博');
      expect(like.comment, isNull);
      expect(like.statusComplete, isTrue);
    });

    test('a cut-down post still shows, but is marked so', () {
      final like = LikeNotice.tryFromJson(likeNoticeJson(cut: true))!;

      expect(like.status?.text, '被赞的微博');
      expect(like.status?.user?.screenName, '示例用户');
      expect(like.statusComplete, isFalse);
    });

    test('a like of a comment takes the post from the comment', () {
      final like = LikeNotice.tryFromJson(
        likeNoticeJson(
          comment: {
            ...commentJson(id: 5, text: '被赞的评论'),
            'status': statusJson(id: 6, text: '评论所在的微博'),
          },
        ),
      )!;

      expect(like.comment?.text, '被赞的评论');
      expect(like.status?.id, '6');
    });
  });
}

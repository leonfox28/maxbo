import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/comment.dart';

import '../../support/statuses.dart';

void main() {
  test('parses a comment with its thread preview', () {
    final c = Comment.tryFromJson(
      commentJson(
        id: 6000000000000042,
        text: '说得好',
        likes: 7,
        replyCount: 5,
        replies: [
          commentJson(
            id: 2,
            text: '回复<a href=/n/评论者 usercard="name=评论者">@评论者</a>:同意',
          ),
        ],
      ),
    )!;

    expect(c.id, '6000000000000042');
    expect(c.user?.screenName, '评论者');
    expect(c.createdAt, DateTime.utc(2026, 10, 12, 5, 20));
    expect(c.source, '来自上海');
    expect(c.text, '说得好');
    expect(c.likesCount, 7);
    expect(c.replyCount, 5);
    expect(c.replies.single.id, '2');
    expect(c.replies.single.text, startsWith('回复'));
    expect(c.pics, isEmpty);
  });

  test('reads attached pictures from url_struct', () {
    final c = Comment.tryFromJson(
      commentJson(urlStruct: [commentPicJson('p1')]),
    )!;

    final pic = c.pics.single;
    expect(pic.id, 'p1');
    // No sizes: renditions stay smallest first, and the smallest is used.
    expect(pic.variantFor(300).url, 'https://wx4.sinaimg.cn/thumbnail/p1.jpg');
    expect(pic.viewerVariant.url, 'https://wx4.sinaimg.cn/large/p1.jpg');
    expect(pic.largest?.url, 'https://wx4.sinaimg.cn/woriginal/p1.jpg');
    // The entry still resolves its short link.
    expect(c.urls.single.shortUrl, 'http://t.cn/p1');
  });

  test('skips the picture of the comment a reply answers', () {
    // Weibo copies the answered comment's url_struct entry into the reply,
    // without linking it from text_raw or counting it in pic_num.
    final copied = {
      ...commentJson(urlStruct: [commentPicJson('root')]),
      'text_raw': '回复',
      'pic_num': 0,
    };
    expect(Comment.tryFromJson(copied)!.pics, isEmpty);

    final ownToo = {
      ...commentJson(urlStruct: [commentPicJson('root'), commentPicJson('p2')]),
      'text_raw': '回复 http://t.cn/p2',
      'pic_num': 1,
    };
    expect(Comment.tryFromJson(ownToo)!.pics.map((p) => p.id), ['p2']);
  });

  test('tolerates missing and mistyped fields', () {
    expect(Comment.tryFromJson(null), isNull);
    expect(Comment.tryFromJson({'text': 'no id'}), isNull);

    final c = Comment.tryFromJson({
      'id': 9,
      'user': [],
      'like_counts': '3',
      'total_number': null,
      'comments': 'none',
      'url_struct': {},
    })!;
    expect(c.id, '9');
    expect(c.user, isNull);
    expect(c.likesCount, 3);
    expect(c.replyCount, 0);
    expect(c.replies, isEmpty);
    expect(c.pics, isEmpty);
  });
}

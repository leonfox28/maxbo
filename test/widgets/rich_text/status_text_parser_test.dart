import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/status.dart';
import 'package:maxbo/widgets/rich_text/status_text_parser.dart';

/// A compact, comparable rendering of segments.
List<String> describe(List<TextSegment> segments) => [
  for (final s in segments)
    switch (s) {
      PlainSegment(:final text) => 'text:$text',
      EmoticonSegment(:final url, :final alt) => 'emoticon:$alt@$url',
      ExpandSegment() => 'expand',
      LinkSegment(:final text, :final link, :final iconUrl) =>
        'link:$text→${switch (link) {
          MentionLink(:final screenName) => 'user:$screenName',
          TopicLink(:final topic) => 'topic:$topic',
          ImageLink(:final url) => 'image:$url',
          WebLink(:final url) => 'web:$url',
        }}${iconUrl == null ? '' : ' icon'}',
    },
];

void main() {
  test('plain text with line breaks and entities', () {
    expect(describe(parseStatusText('第一行<br />第二行 &amp; &lt;b&gt;')), [
      'text:第一行\n第二行 & <b>',
    ]);
  });

  test('mentions with unquoted href', () {
    expect(
      describe(
        parseStatusText('你好<a href=/n/示例用户 usercard="name=示例用户">@示例用户</a> 再见'),
      ),
      ['text:你好', 'link:@示例用户→user:示例用户', 'text: 再见'],
    );
  });

  test('topics link by their label', () {
    expect(
      describe(
        parseStatusText(
          '<a href="//s.weibo.com/weibo?q=%23示例话题%23" target="_blank">#示例话题#</a>',
        ),
      ),
      ['link:#示例话题#→topic:示例话题'],
    );
  });

  test('emoticons become images with their alt text', () {
    expect(
      describe(
        parseStatusText(
          '好<img alt="[笑]" title="[笑]" src="//face.t.sinajs.cn/xiao.png" />',
        ),
      ),
      ['text:好', 'emoticon:[笑]@https://face.t.sinajs.cn/xiao.png'],
    );
    // Without a usable src, the alt text stands in.
    expect(describe(parseStatusText('<img alt="[笑]" src="">')), ['text:[笑]']);
  });

  test('short links resolve through url_struct and keep their icon', () {
    const urls = [
      UrlEntity(
        shortUrl: 'http://t.cn/AbCd',
        longUrl: 'https://example.com/article',
        title: '网页链接',
      ),
    ];
    expect(
      describe(
        parseStatusText(
          '看 <a target="_blank" href="http://t.cn/AbCd"><img class="icon-link" src="https://h5.sinaimg.cn/web.png"/>网页链接</a>',
          urls: urls,
        ),
      ),
      ['text:看 ', 'link:网页链接→web:https://example.com/article icon'],
    );
    // An unknown short link opens as is.
    expect(describe(parseStatusText('<a href="http://t.cn/Zz">网页链接</a>')), [
      'link:网页链接→web:http://t.cn/Zz',
    ]);
  });

  test('picture links and app-only links', () {
    expect(
      describe(
        parseStatusText(
          '<a target="_blank" data-pid="p1" href="https://wx1.sinaimg.cn/large/p1.jpg">查看图片</a>'
          '<a href="sinaweibo://searchall?q=x">打开客户端</a>',
        ),
      ),
      ['link:查看图片→image:https://wx1.sinaimg.cn/large/p1.jpg', 'text:打开客户端'],
    );
  });

  test('a truncated post ends with expand', () {
    expect(
      describe(parseStatusText('很长的内容...<span class="expand">展开</span>')),
      ['text:很长的内容...', 'expand'],
    );
  });

  test('unknown elements keep their text', () {
    expect(describe(parseStatusText('<b>粗</b><span>体</span>')), ['text:粗体']);
  });

  test('plainStatusText flattens a body to one line', () {
    expect(
      plainStatusText(
        '看<a href=/n/某人 usercard="name=某人">@某人</a> '
        '<img alt="[doge]" src="https://face.t.sinajs.cn/doge.png">'
        '<br>第二行<span class="expand">展开</span>',
      ),
      '看@某人 [doge] 第二行',
    );
  });
}

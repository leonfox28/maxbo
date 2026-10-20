import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/topic.dart';

import '../../support/statuses.dart';

void main() {
  test('reads a topic: its lead, picture and all-time counts', () {
    final topic = Topic.tryFromJson(
      topicHeadsJson(name: '某个话题', summary: '话题导语'),
    )!;

    expect(topic.name, '某个话题');
    expect(topic.summary, '话题导语');
    expect(topic.imageUrl, 'https://wx1.sinaimg.cn/thumb180/topic.jpg');
    // Not the 24-hour counts.
    expect(topic.reads, 42262434);
    expect(topic.mentions, 9365);
    expect(topic.authors, 1997);
  });

  test('an empty lead is none', () {
    expect(Topic.tryFromJson(topicHeadsJson(name: '某个话题'))!.summary, isNull);
  });

  test('a word searched for is no topic, counts and all', () {
    expect(Topic.tryFromJson(topicHeadsJson()), isNull);
    expect(Topic.tryFromJson(null), isNull);
    expect(Topic.tryFromJson(<String, Object?>{}), isNull);
  });
}

import '../../core/util/json_read.dart';

/// A `#话题#` as a search for it describes it ("导语", how many read it).
class Topic {
  const Topic({
    required this.name,
    this.summary,
    this.imageUrl,
    this.reads,
    this.mentions,
    this.authors,
  });

  /// The topic without its `#`.
  final String name;

  /// Its lead ("导语").
  final String? summary;
  final String? imageUrl;

  /// How many times it was read ("阅读"), all time.
  final int? reads;

  /// How many posts discuss it ("讨论").
  final int? mentions;

  /// How many people posted in it ("原创人数").
  final int? authors;

  /// Reads the `topicHeads` of `statuses/search`. Searching a plain word
  /// gets one too, with counts but no name: null then, as it is no topic.
  static Topic? tryFromJson(Object? json) {
    final map = readMap(json);
    final object = readMap(map?['object']);
    final name = readString(object?['display_name']);
    if (map == null || name == null) return null;
    final counts = readMap(map['count']);
    return Topic(
      name: name,
      summary: readString(object?['summary']),
      imageUrl: readUrl(readMap(object?['image'])?['url']),
      reads: readInt(counts?['read']),
      mentions: readInt(counts?['mention']),
      authors: readInt(counts?['ori_uv']),
    );
  }
}

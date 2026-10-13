import '../../core/util/json_read.dart';
import 'status.dart';

/// One page of a timeline.
class FeedPage {
  const FeedPage({required this.statuses, this.nextCursor});

  final List<Status> statuses;

  /// The `max_id` that fetches the next, older page; null at the end.
  final String? nextCursor;

  factory FeedPage.fromJson(Map<String, Object?> json) {
    final statuses = [
      for (final s in readMaps(json['statuses'])) ?Status.tryFromJson(s),
    ];
    // The `_str` twins carry the same ids without risking number precision.
    final maxId = readString(json['max_id_str']) ?? readString(json['max_id']);
    return FeedPage(
      statuses: statuses,
      nextCursor: statuses.isEmpty || maxId == null || maxId == '0'
          ? null
          : maxId,
    );
  }
}

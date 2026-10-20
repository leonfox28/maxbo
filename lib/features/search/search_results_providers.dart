import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/paging/paged_controller.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/paged.dart';
import '../../data/models/status.dart';
import '../../data/models/topic.dart';

/// The posts found for a query, newest first.
final searchResultsProvider = AsyncNotifierProvider.autoDispose
    .family<SearchResultsController, PagedState<Status>, String>(
      SearchResultsController.new,
    );

class SearchResultsController extends PagedController<Status> {
  SearchResultsController(this.query);

  final String query;

  /// The topic searched for, as the first page describes it; null when the
  /// query is no topic.
  Topic? topic;

  @override
  Future<Paged<Status>> fetchPage(String? cursor) async {
    final results = await ref
        .read(weiboApiProvider)
        .searchStatuses(query, page: int.parse(cursor ?? '1'));
    if (cursor == null) topic = results.topic;
    return results.statuses;
  }

  @override
  String idOf(Status item) => item.id;
}

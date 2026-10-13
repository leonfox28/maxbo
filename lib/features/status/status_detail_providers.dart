import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/paging/paged_controller.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/paged.dart';
import '../../data/models/status.dart';

/// A status as its detail page shows it: full text and current counts.
final statusDetailProvider = FutureProvider.autoDispose.family<Status, String>(
  (ref, id) => ref.read(weiboApiProvider).fetchStatus(id),
);

/// The reposts of a status, by status id.
final repostsProvider = AsyncNotifierProvider.autoDispose
    .family<RepostsController, PagedState<Status>, String>(
      RepostsController.new,
    );

class RepostsController extends PagedController<Status> {
  RepostsController(this.statusId);

  final String statusId;

  @override
  Future<Paged<Status>> fetchPage(String? cursor) => ref
      .read(weiboApiProvider)
      .fetchReposts(statusId: statusId, page: int.parse(cursor ?? '1'));

  @override
  String idOf(Status item) => item.id;
}

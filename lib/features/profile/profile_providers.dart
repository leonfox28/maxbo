import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/paging/paged_controller.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/paged.dart';
import '../../data/models/status.dart';
import '../../data/models/user_profile.dart';

/// Which profile to show: the user with [uid], or else the one named
/// [screenName].
typedef ProfileQuery = ({String? uid, String? screenName});

final profileProvider = FutureProvider.autoDispose
    .family<UserProfile, ProfileQuery>(
      (ref, query) => ref
          .read(weiboApiProvider)
          .fetchProfile(uid: query.uid, screenName: query.screenName),
    );

/// The statuses of a user, by uid.
final userStatusesProvider = AsyncNotifierProvider.autoDispose
    .family<UserStatusesController, PagedState<Status>, String>(
      UserStatusesController.new,
    );

class UserStatusesController extends PagedController<Status> {
  UserStatusesController(this.uid);

  final String uid;

  @override
  Future<Paged<Status>> fetchPage(String? cursor) => ref
      .read(weiboApiProvider)
      .fetchUserStatuses(uid: uid, page: int.parse(cursor ?? '1'));

  @override
  String idOf(Status item) => item.id;
}

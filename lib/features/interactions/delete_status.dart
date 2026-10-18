import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_exception.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/status.dart';
import '../../widgets/load_state.dart';
import '../profile/profile_providers.dart';
import '../timeline/timeline_controller.dart';
import 'comment_composer.dart';

// Statuses being deleted, so a second tap doesn't ask Weibo again.
final _deleting = <String>{};

/// Offers to delete [status], which the user posted, and asks again before
/// it goes. Once deleted it leaves the open lists of the user's posts, and
/// [onDeleted] is called, e.g. to leave its detail page.
Future<void> deleteOwnStatus(
  BuildContext context,
  Status status, {
  VoidCallback? onDeleted,
}) async {
  if (_deleting.contains(status.id)) return;
  if (!await confirmDelete(context, question: '确定删除这条微博吗？') ||
      !context.mounted) {
    return;
  }
  final container = ProviderScope.containerOf(context, listen: false);
  _deleting.add(status.id);
  try {
    await container.read(weiboApiProvider).deleteStatus(status.id);
  } on WeiboException catch (e) {
    if (context.mounted) showErrorSnackBar(context, e);
    return;
  } finally {
    _deleting.remove(status.id);
  }
  // Only lists already open: nothing loads for it (PLAN §2).
  if (container.exists(timelineProvider)) {
    container.read(timelineProvider.notifier).remove(status.id);
  }
  final uid = status.user?.id;
  if (uid != null && container.exists(userStatusesProvider(uid))) {
    container.read(userStatusesProvider(uid).notifier).remove(status.id);
  }
  if (!context.mounted) return;
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(const SnackBar(content: Text('已删除')));
  onDeleted?.call();
}

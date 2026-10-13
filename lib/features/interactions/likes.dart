import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../data/api/weibo_api.dart';
import '../../widgets/load_state.dart';

enum LikeKind { status, comment }

/// A status or comment, which can be liked.
typedef LikeTarget = ({LikeKind kind, String id});

/// Whether something is liked by the user, and by how many.
@immutable
class LikeState {
  const LikeState({required this.liked, required this.count});

  final bool liked;
  final int count;

  LikeState get toggled =>
      LikeState(liked: !liked, count: max(0, count + (liked ? -1 : 1)));

  @override
  bool operator ==(Object other) =>
      other is LikeState && other.liked == liked && other.count == count;

  @override
  int get hashCode => Object.hash(liked, count);
}

/// A like the user changed in the app: [current], over what the loaded data
/// said ([base]) when they did.
class LikeChange {
  const LikeChange({
    required this.base,
    required this.current,
    this.pending = false,
  });

  final LikeState base;
  final LikeState current;

  /// Whether Weibo has yet to confirm it.
  final bool pending;
}

/// The likes the user changed, shared by every list and page showing the
/// same status or comment.
final likesProvider =
    NotifierProvider<LikesController, Map<LikeTarget, LikeChange>>(
      LikesController.new,
    );

class LikesController extends Notifier<Map<LikeTarget, LikeChange>> {
  @override
  Map<LikeTarget, LikeChange> build() {
    // Another account has likes of its own.
    ref.watch(sessionProvider);
    return const {};
  }

  /// What to show for something that the loaded data says is [loaded].
  ///
  /// A change applies only over the data it was made on: data loaded since
  /// already has it, and is newer.
  static LikeState resolve(LikeChange? change, LikeState loaded) =>
      change != null && change.base == loaded ? change.current : loaded;

  /// Likes [target], or takes the like back, when the loaded data says it is
  /// [loaded].
  ///
  /// The change shows at once and is taken back if Weibo refuses it, with
  /// the [WeiboException] rethrown. Does nothing while a change of [target]
  /// is on its way.
  Future<void> toggle(LikeTarget target, LikeState loaded) async {
    final previous = state[target];
    if (previous?.pending ?? false) return;
    final next = resolve(previous, loaded).toggled;
    _set(target, LikeChange(base: loaded, current: next, pending: true));
    final api = ref.read(weiboApiProvider);
    try {
      switch (target.kind) {
        case LikeKind.status:
          await api.likeStatus(target.id, liked: next.liked);
        case LikeKind.comment:
          await api.likeComment(target.id, liked: next.liked);
      }
      _set(target, LikeChange(base: loaded, current: next));
    } on WeiboException {
      _set(target, previous);
      rethrow;
    }
  }

  void _set(LikeTarget target, LikeChange? change) => state = {
    for (final MapEntry(:key, :value) in state.entries)
      if (key != target) key: value,
    target: ?change,
  };
}

/// Shows the like of [target], which the loaded data says is [loaded], as
/// the user last left it. [builder] gets a callback to toggle it, or null
/// while a toggle is on its way.
class LikeBuilder extends ConsumerWidget {
  const LikeBuilder({
    super.key,
    required this.target,
    required this.loaded,
    required this.builder,
  });

  final LikeTarget target;
  final LikeState loaded;
  final Widget Function(
    BuildContext context,
    LikeState like,
    VoidCallback? onToggle,
  )
  builder;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final change = ref.watch(likesProvider.select((likes) => likes[target]));
    final pending = change?.pending ?? false;
    return builder(
      context,
      LikesController.resolve(change, loaded),
      pending ? null : () => _toggle(context, ref),
    );
  }

  Future<void> _toggle(BuildContext context, WidgetRef ref) async {
    try {
      await ref.read(likesProvider.notifier).toggle(target, loaded);
    } on WeiboException catch (e) {
      if (context.mounted) showErrorSnackBar(context, e);
    }
  }
}

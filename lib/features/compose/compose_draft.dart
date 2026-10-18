import 'dart:io';
import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/session/session_controller.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/status.dart';
import '../profile/profile_providers.dart';
import '../timeline/timeline_controller.dart';

/// How many pictures a post can have.
const maxDraftPictures = 9;

/// A picture picked for a new post, by its file; [pid] once uploaded.
class DraftPicture {
  const DraftPicture(this.path, {this.pid});

  final String path;
  final String? pid;
}

/// The post the user is writing.
class ComposeDraft {
  const ComposeDraft({
    this.text = '',
    this.pictures = const [],
    this.visibility = StatusVisibility.public,
  });

  final String text;
  final List<DraftPicture> pictures;
  final StatusVisibility visibility;

  /// Weibo wants some text, pictures or not, as weibo.com's composer does.
  bool get canSend => text.trim().isNotEmpty;

  ComposeDraft copyWith({
    String? text,
    List<DraftPicture>? pictures,
    StatusVisibility? visibility,
  }) => ComposeDraft(
    text: text ?? this.text,
    pictures: pictures ?? this.pictures,
    visibility: visibility ?? this.visibility,
  );
}

/// The draft of a new post. It outlives the compose page, so closing it
/// loses nothing, and starts over for another account.
final composeDraftProvider =
    NotifierProvider<ComposeDraftController, ComposeDraft>(
      ComposeDraftController.new,
    );

class ComposeDraftController extends Notifier<ComposeDraft> {
  @override
  ComposeDraft build() {
    ref.watch(
      sessionProvider.select((s) => s is SignedIn ? s.account.uid : null),
    );
    return const ComposeDraft();
  }

  set text(String text) => state = state.copyWith(text: text);

  set visibility(StatusVisibility visibility) =>
      state = state.copyWith(visibility: visibility);

  /// Adds the pictures at [paths], as many as fit, and returns how many
  /// were left out.
  int addPictures(List<String> paths) {
    final room = maxDraftPictures - state.pictures.length;
    state = state.copyWith(
      pictures: [...state.pictures, ...paths.take(room).map(DraftPicture.new)],
    );
    return max(0, paths.length - room);
  }

  void removePicture(int index) =>
      state = state.copyWith(pictures: [...state.pictures]..removeAt(index));

  /// Uploads the pictures not yet uploaded, one at a time, then posts the
  /// draft and clears it. [onUpload] hears which picture (from 0) goes up.
  ///
  /// The new status goes atop the open lists that show the user's posts.
  /// Throws a `WeiboException` on failure; the pictures uploaded by then
  /// keep their pids, so sending again doesn't upload them again.
  Future<void> send({void Function(int index)? onUpload}) async {
    final api = ref.read(weiboApiProvider);
    final pids = <String>[];
    for (var i = 0; i < state.pictures.length; i++) {
      final picture = state.pictures[i];
      var pid = picture.pid;
      if (pid == null) {
        onUpload?.call(i);
        pid = await api.uploadPicture(await File(picture.path).readAsBytes());
        if (!ref.mounted) return;
        _uploaded(picture, pid);
      }
      pids.add(pid);
    }
    final draft = state;
    final status = await api.postStatus(
      text: draft.text.trim(),
      pids: pids,
      visibility: draft.visibility,
    );
    if (!ref.mounted) return;
    state = const ComposeDraft();
    if (status != null) _show(status);
  }

  void _uploaded(DraftPicture picture, String pid) => state = state.copyWith(
    pictures: [
      for (final p in state.pictures)
        identical(p, picture) ? DraftPicture(p.path, pid: pid) : p,
    ],
  );

  // Only lists already open: nothing loads for it (PLAN §2).
  void _show(Status status) {
    if (ref.exists(timelineProvider)) {
      ref.read(timelineProvider.notifier).prepend(status);
    }
    final uid = status.user?.id;
    if (uid != null && ref.exists(userStatusesProvider(uid))) {
      ref.read(userStatusesProvider(uid).notifier).prepend(status);
    }
  }
}

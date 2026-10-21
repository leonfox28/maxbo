import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/status.dart';
import '../../widgets/load_state.dart';
import '../compose/visibility_button.dart';
import '../profile/profile_providers.dart';
import '../status/status_detail_providers.dart';
import '../timeline/timeline_controller.dart';
import 'comment_composer.dart';

/// What the user is writing to repost, by status id. It stays until sent,
/// so closing the sheet loses nothing, and starts over for another account.
final repostDraftsProvider = Provider<Map<String, String>>((ref) {
  ref.watch(
    sessionProvider.select((s) => s is SignedIn ? s.account.uid : null),
  );
  return {};
});

/// Opens a sheet to repost [status], which goes to Weibo when the user taps
/// 转发. The repost then goes atop the open lists it belongs in, and
/// [onReposted] is called.
///
/// Reposting a repost starts the text with the chain it carries on, as
/// weibo.com does: `//@author:their text`.
Future<void> repostStatus(
  BuildContext context,
  Status status, {
  VoidCallback? onReposted,
}) async {
  final container = ProviderScope.containerOf(context, listen: false);
  var quoted = status;
  // Lists cut long posts short, and the chain quotes all of it.
  if (status.retweeted != null && status.plainText == null) {
    try {
      quoted = await container.read(weiboApiProvider).fetchStatus(status.id);
    } on WeiboException catch (e) {
      if (context.mounted) showErrorSnackBar(context, e);
      return;
    }
    if (!context.mounted) return;
  }
  final sent = await showModalBottomSheet<({Status? repost})>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    // Over the tab bar, when asked from a tab.
    useRootNavigator: true,
    builder: (context) => _RepostSheet(status: status, quote: _quoteOf(quoted)),
  );
  if (sent == null) return;
  // Only lists already open: nothing loads for it (PLAN §2).
  if (sent.repost case final repost?) {
    if (container.exists(timelineProvider)) {
      container.read(timelineProvider.notifier).prepend(repost);
    }
    final uid = repost.user?.id;
    if (uid != null && container.exists(userStatusesProvider(uid))) {
      container.read(userStatusesProvider(uid).notifier).prepend(repost);
    }
    if (container.exists(repostsProvider(status.id))) {
      container.read(repostsProvider(status.id).notifier).prepend(repost);
    }
  }
  if (context.mounted) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(const SnackBar(content: Text('转发成功')));
  }
  onReposted?.call();
}

/// The chain a repost of [status] carries on, empty if it isn't a repost.
String _quoteOf(Status status) {
  final name = status.user?.screenName;
  final text = status.plainText;
  if (status.retweeted == null || name == null || text == null) return '';
  return '//@$name:$text';
}

class _RepostSheet extends ConsumerStatefulWidget {
  const _RepostSheet({required this.status, required this.quote});

  final Status status;

  /// What the text starts as, when there is no draft.
  final String quote;

  @override
  ConsumerState<_RepostSheet> createState() => _RepostSheetState();
}

class _RepostSheetState extends ConsumerState<_RepostSheet> {
  late final _drafts = ref.read(repostDraftsProvider);
  late final _text = TextEditingController(
    text: _drafts[widget.status.id] ?? widget.quote,
  )..selection = const TextSelection.collapsed(offset: 0);
  var _visibility = StatusVisibility.public;
  var _alsoComment = false;
  bool _sending = false;
  WeiboException? _error;

  @override
  void initState() {
    super.initState();
    _text.addListener(() => _drafts[widget.status.id] = _text.text);
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      final repost = await ref
          .read(weiboApiProvider)
          .repostStatus(
            id: widget.status.id,
            text: _text.text.trim(),
            alsoComment: _alsoComment,
            visibility: _visibility,
          );
      _drafts.remove(widget.status.id);
      if (mounted) Navigator.pop(context, (repost: repost));
    } on WeiboException catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final error = _error;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        0,
        16,
        16 + MediaQuery.viewInsetsOf(context).bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: _text,
            autofocus: true,
            enabled: !_sending,
            minLines: 3,
            maxLines: 6,
            textInputAction: TextInputAction.newline,
            decoration: const InputDecoration(
              // Weibo puts in 转发微博 when there is nothing.
              hintText: '说说分享心得…',
              border: OutlineInputBorder(),
            ),
          ),
          if (error != null) SendError(error),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    VisibilityButton(
                      value: _visibility,
                      onChanged: _sending
                          ? null
                          : (v) => setState(() => _visibility = v),
                    ),
                    MergeSemantics(
                      child: InkWell(
                        onTap: _sending
                            ? null
                            : () =>
                                  setState(() => _alsoComment = !_alsoComment),
                        borderRadius: BorderRadius.circular(4),
                        child: Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Checkbox(
                                value: _alsoComment,
                                onChanged: _sending
                                    ? null
                                    : (v) => setState(
                                        () => _alsoComment = v ?? false,
                                      ),
                              ),
                              const Text('同时评论'),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              FilledButton(
                onPressed: _sending ? null : _send,
                child: _sending
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('转发'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

import 'package:flutter/material.dart';

import '../../core/net/weibo_exception.dart';
import '../../widgets/floating_pill.dart';
import '../webview/web_page.dart';

/// Opens a sheet to write a comment or reply, which [send] posts when the
/// user taps 发送. Completes with true once it is sent.
///
/// What the user writes is kept in [drafts] under [draftKey] until it is
/// sent, so closing the sheet loses nothing.
Future<bool> showCommentComposer(
  BuildContext context, {
  required String hint,
  required Future<void> Function(String text) send,
  required Map<String, String> drafts,
  required String draftKey,
}) async {
  final sent = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) =>
        _Composer(hint: hint, send: send, drafts: drafts, draftKey: draftKey),
  );
  return sent ?? false;
}

class _Composer extends StatefulWidget {
  const _Composer({
    required this.hint,
    required this.send,
    required this.drafts,
    required this.draftKey,
  });

  final String hint;
  final Future<void> Function(String text) send;
  final Map<String, String> drafts;
  final String draftKey;

  @override
  State<_Composer> createState() => _ComposerState();
}

class _ComposerState extends State<_Composer> {
  late final _text = TextEditingController(
    text: widget.drafts[widget.draftKey],
  );
  bool _sending = false;
  WeiboException? _error;

  @override
  void initState() {
    super.initState();
    _text.addListener(() {
      widget.drafts[widget.draftKey] = _text.text;
      setState(() {});
    });
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
      await widget.send(_text.text.trim());
      widget.drafts.remove(widget.draftKey);
      if (mounted) Navigator.pop(context, true);
    } on WeiboException catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final error = _error;
    final canSend = !_sending && _text.text.trim().isNotEmpty;
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
            decoration: InputDecoration(
              hintText: widget.hint,
              border: const OutlineInputBorder(),
            ),
          ),
          if (error != null) SendError(error),
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton(
              onPressed: canSend ? _send : null,
              child: _sending
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('发送'),
            ),
          ),
        ],
      ),
    );
  }
}

/// Why sending what the user wrote failed, under it, with a way to take
/// the check when Weibo asks for one.
class SendError extends StatelessWidget {
  const SendError(this.error, {super.key});

  final WeiboException error;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final error = this.error;
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        children: [
          Expanded(
            child: Text(
              error.message,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.error,
              ),
            ),
          ),
          if (error case CaptchaRequiredException(:final url))
            TextButton(
              // The user takes the check, then sends again themselves
              // (PLAN §2).
              onPressed: () => openWebPage(context, url),
              child: const Text('去验证'),
            ),
        ],
      ),
    );
  }
}

/// The bar floating under a status or a thread: a field that opens the
/// composer, and optionally [trailing] buttons.
class CommentBar extends StatelessWidget {
  const CommentBar({
    super.key,
    required this.hint,
    required this.onTap,
    this.trailing,
  });

  final String hint;
  final VoidCallback onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final trailing = this.trailing;
    return FloatingPill(
      stretch: true,
      child: Row(
        children: [
          Expanded(
            // A field look-alike, as tall as the tab bar's items.
            child: Material(
              color: theme.colorScheme.surface,
              shape: const StadiumBorder(),
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: onTap,
                child: Container(
                  height: 48,
                  alignment: Alignment.centerLeft,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Text(
                    hint,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ),
            ),
          ),
          if (trailing != null) ...[const SizedBox(width: 4), trailing],
        ],
      ),
    );
  }
}

/// Offers to delete something the user wrote, and asks [question] before
/// it goes. Completes with true when they confirmed.
Future<bool> confirmDelete(
  BuildContext context, {
  String question = '确定删除这条评论吗？',
}) async {
  final chosen = await showModalBottomSheet<bool>(
    context: context,
    // Over the tab bar, when asked from a tab.
    useRootNavigator: true,
    builder: (context) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.delete_outline),
            title: const Text('删除'),
            onTap: () => Navigator.pop(context, true),
          ),
          ListTile(
            leading: const Icon(Icons.close),
            title: const Text('取消'),
            onTap: () => Navigator.pop(context, false),
          ),
        ],
      ),
    ),
  );
  if (chosen != true || !context.mounted) return false;
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      content: Text(question),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('取消'),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, true),
          child: const Text('删除'),
        ),
      ],
    ),
  );
  return confirmed ?? false;
}

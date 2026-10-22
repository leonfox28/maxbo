import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/util/dev_log.dart';
import '../../data/api/weibo_api.dart';
import '../../widgets/floating_pill.dart';
import '../compose/compose_draft.dart';
import '../compose/picture_picker.dart';
import '../webview/web_page.dart';

/// A comment or reply the user is writing: text, and a picture.
class CommentDraft {
  const CommentDraft({this.text = '', this.picture});

  final String text;
  final DraftPicture? picture;

  /// A picture can go without text, as on weibo.com.
  bool get canSend => text.trim().isNotEmpty || picture != null;
}

/// Opens a sheet to write a comment or reply on status [statusId], which
/// [send] posts when the user taps 发送, with the pid of its picture once
/// uploaded. Completes with true once it is sent.
///
/// What the user writes is kept in [drafts] under [draftKey] until it is
/// sent, so closing the sheet loses nothing.
Future<bool> showCommentComposer(
  BuildContext context, {
  required String statusId,
  required String hint,
  required Future<void> Function(String text, String? pid) send,
  required Map<String, CommentDraft> drafts,
  required String draftKey,
}) async {
  final sent = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => _Composer(
      statusId: statusId,
      hint: hint,
      send: send,
      drafts: drafts,
      draftKey: draftKey,
    ),
  );
  return sent ?? false;
}

class _Composer extends ConsumerStatefulWidget {
  const _Composer({
    required this.statusId,
    required this.hint,
    required this.send,
    required this.drafts,
    required this.draftKey,
  });

  final String statusId;
  final String hint;
  final Future<void> Function(String text, String? pid) send;
  final Map<String, CommentDraft> drafts;
  final String draftKey;

  @override
  ConsumerState<_Composer> createState() => _ComposerState();
}

class _ComposerState extends ConsumerState<_Composer> {
  late final _text = TextEditingController(text: _draft.text);
  bool _sending = false;
  bool _uploading = false;

  /// Whether Weibo said comments here may carry a picture, once asked.
  bool? _pictureAllowed;
  bool _asking = false;
  String? _notice;
  WeiboException? _error;

  CommentDraft get _draft =>
      widget.drafts[widget.draftKey] ?? const CommentDraft();

  set _draft(CommentDraft draft) => widget.drafts[widget.draftKey] = draft;

  @override
  void initState() {
    super.initState();
    _text.addListener(() {
      _draft = CommentDraft(text: _text.text, picture: _draft.picture);
      setState(() {});
    });
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  Future<void> _pick() async {
    setState(() {
      _notice = null;
      _error = null;
    });
    // Asked only when the user wants a picture, so opening the sheet
    // requests nothing (PLAN §2).
    if (_pictureAllowed == null) {
      setState(() => _asking = true);
      try {
        _pictureAllowed = await ref
            .read(weiboApiProvider)
            .allowsCommentPicture(widget.statusId);
      } on WeiboException catch (e) {
        // As on weibo.com: Weibo still has its say when it is sent.
        devLog('checkAllowCommentWithPic failed: ${e.runtimeType}');
      } finally {
        if (mounted) setState(() => _asking = false);
      }
      if (!mounted) return;
    }
    if (_pictureAllowed == false) {
      setState(() => _notice = '这条微博的评论不能带图');
      return;
    }
    final List<String> paths;
    try {
      paths = await ref.read(picturePickerProvider)(1);
    } on PlatformException catch (e) {
      devLog('picking a picture failed: ${e.code}');
      if (mounted) setState(() => _notice = '无法打开相册');
      return;
    }
    if (!mounted || paths.isEmpty) return;
    setState(
      () => _draft = CommentDraft(
        text: _draft.text,
        picture: DraftPicture(paths.first),
      ),
    );
  }

  void _removePicture() =>
      setState(() => _draft = CommentDraft(text: _draft.text));

  Future<void> _send() async {
    final api = ref.read(weiboApiProvider);
    setState(() {
      _sending = true;
      _notice = null;
      _error = null;
    });
    try {
      var picture = _draft.picture;
      if (picture != null && picture.pid == null) {
        setState(() => _uploading = true);
        final pid = await api.uploadPicture(
          await File(picture.path).readAsBytes(),
        );
        // Kept, so sending again after a failure doesn't upload it again.
        picture = DraftPicture(picture.path, pid: pid);
        _draft = CommentDraft(text: _draft.text, picture: picture);
        if (mounted) setState(() => _uploading = false);
      }
      await widget.send(_text.text.trim(), picture?.pid);
      widget.drafts.remove(widget.draftKey);
      if (mounted) Navigator.pop(context, true);
    } on WeiboException catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) {
        setState(() {
          _sending = false;
          _uploading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final error = _error;
    final notice = _notice;
    final picture = _draft.picture;
    final canSend = !_sending && _draft.canSend;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        0,
        16,
        // Above the keyboard, or else the system's gesture bar.
        16 +
            MediaQuery.viewInsetsOf(context).bottom +
            MediaQuery.paddingOf(context).bottom,
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
          if (notice != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                notice,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          const SizedBox(height: 8),
          Row(
            children: [
              if (picture != null)
                _PictureThumb(
                  picture,
                  uploading: _uploading,
                  onRemove: _sending ? null : _removePicture,
                )
              else
                IconButton(
                  onPressed: _sending || _asking || _pictureAllowed == false
                      ? null
                      : _pick,
                  tooltip: '添加图片',
                  icon: _asking
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.add_photo_alternate_outlined),
                ),
              const Spacer(),
              FilledButton(
                onPressed: canSend ? _send : null,
                child: _sending
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('发送'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// The picked picture, small, with a way to take it out.
class _PictureThumb extends StatelessWidget {
  const _PictureThumb(
    this.picture, {
    required this.uploading,
    required this.onRemove,
  });

  final DraftPicture picture;
  final bool uploading;
  final VoidCallback? onRemove;

  static const _side = 64.0;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final onRemove = this.onRemove;
    return SizedBox.square(
      dimension: _side,
      child: Stack(
        fit: StackFit.expand,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: Image.file(
              File(picture.path),
              fit: BoxFit.cover,
              cacheWidth: (_side * MediaQuery.devicePixelRatioOf(context))
                  .round(),
              errorBuilder: (_, _, _) => ColoredBox(
                color: theme.colorScheme.surfaceContainerHighest,
                child: const Icon(Icons.broken_image_outlined),
              ),
            ),
          ),
          if (uploading)
            const ColoredBox(
              color: Colors.black38,
              child: Center(
                child: SizedBox.square(
                  dimension: 24,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            ),
          if (onRemove != null)
            Positioned(
              top: 2,
              right: 2,
              child: IconButton.filledTonal(
                onPressed: onRemove,
                tooltip: '移除',
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints.tightFor(
                  width: 22,
                  height: 22,
                ),
                style: IconButton.styleFrom(
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
                iconSize: 14,
                icon: const Icon(Icons.close),
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

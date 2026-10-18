import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/util/dev_log.dart';
import '../../data/models/status.dart';
import '../../widgets/adaptive_layout.dart';
import '../webview/web_page.dart';
import 'compose_draft.dart';

/// Picks pictures from the gallery, at most [limit]; overridden in tests.
final picturePickerProvider =
    Provider<Future<List<String>> Function(int limit)>(
      (ref) => (limit) async {
        final picker = ImagePicker();
        // Without full metadata iOS asks for no photo library access.
        final files = limit > 1
            ? await picker.pickMultiImage(
                limit: limit,
                requestFullMetadata: false,
              )
            : [
                ?await picker.pickImage(
                  source: ImageSource.gallery,
                  requestFullMetadata: false,
                ),
              ];
        return [for (final f in files) f.path];
      },
    );

/// Writes a new post: text, up to nine pictures, and who can see it.
///
/// Nothing goes to Weibo until the user taps 发送; the pictures upload
/// then, one at a time. The draft outlives the page ([composeDraftProvider]).
class ComposePage extends ConsumerStatefulWidget {
  const ComposePage({super.key});

  @override
  ConsumerState<ComposePage> createState() => _ComposePageState();
}

class _ComposePageState extends ConsumerState<ComposePage> {
  late final _text = TextEditingController(
    text: ref.read(composeDraftProvider).text,
  );
  bool _sending = false;

  /// The picture going up, from 0, while sending.
  int? _uploading;
  WeiboException? _error;

  @override
  void initState() {
    super.initState();
    _text.addListener(() {
      // It also hears the cursor move.
      final draft = ref.read(composeDraftProvider.notifier);
      if (ref.read(composeDraftProvider).text != _text.text) {
        draft.text = _text.text;
      }
    });
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  Future<void> _pick() async {
    final draft = ref.read(composeDraftProvider);
    final room = maxDraftPictures - draft.pictures.length;
    if (room <= 0) return;
    final List<String> paths;
    try {
      paths = await ref.read(picturePickerProvider)(room);
    } on PlatformException catch (e) {
      devLog('picking pictures failed: ${e.code}');
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('无法打开相册')));
      }
      return;
    }
    if (mounted) ref.read(composeDraftProvider.notifier).addPictures(paths);
  }

  Future<void> _send() async {
    FocusScope.of(context).unfocus();
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      await ref
          .read(composeDraftProvider.notifier)
          .send(onUpload: (i) => setState(() => _uploading = i));
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('已发送')));
      Navigator.pop(context);
    } on WeiboException catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) {
        setState(() {
          _sending = false;
          _uploading = null;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final draft = ref.watch(composeDraftProvider);
    final error = _error;
    final uploading = _uploading;
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          onPressed: () => Navigator.pop(context),
          tooltip: '关闭',
          icon: const Icon(Icons.close),
        ),
        title: const Text('发微博'),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: FilledButton(
              onPressed: draft.canSend && !_sending ? _send : null,
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
      body: SafeArea(
        child: ReadingList(
          builder: (context, padding) => ListView(
            padding: padding.add(const EdgeInsets.fromLTRB(16, 8, 16, 16)),
            children: [
              TextField(
                controller: _text,
                autofocus: true,
                enabled: !_sending,
                minLines: 5,
                maxLines: null,
                keyboardType: TextInputType.multiline,
                decoration: const InputDecoration(
                  hintText: '分享新鲜事…',
                  border: InputBorder.none,
                ),
              ),
              const SizedBox(height: 8),
              _PictureGrid(
                pictures: draft.pictures,
                uploading: uploading,
                enabled: !_sending,
                onAdd: _pick,
                onRemove: ref.read(composeDraftProvider.notifier).removePicture,
              ),
              const SizedBox(height: 12),
              Align(
                alignment: AlignmentDirectional.centerStart,
                child: _VisibilityButton(
                  value: draft.visibility,
                  onChanged: _sending
                      ? null
                      : (v) =>
                            ref.read(composeDraftProvider.notifier).visibility =
                                v,
                ),
              ),
              if (uploading != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    '正在上传图片 ${uploading + 1}/${draft.pictures.length}',
                    style: theme.textTheme.bodySmall,
                  ),
                ),
              if (error != null)
                Padding(
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
                          // The user takes the check, then sends again
                          // themselves (PLAN §2).
                          onPressed: () => openWebPage(context, url),
                          child: const Text('去验证'),
                        ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// The picked pictures in rows of three, with a tile to add more while
/// there is room.
class _PictureGrid extends StatelessWidget {
  const _PictureGrid({
    required this.pictures,
    required this.uploading,
    required this.enabled,
    required this.onAdd,
    required this.onRemove,
  });

  final List<DraftPicture> pictures;
  final int? uploading;
  final bool enabled;
  final VoidCallback onAdd;
  final ValueChanged<int> onRemove;

  static const _gap = 4.0;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final side = (constraints.maxWidth - 2 * _gap) / 3;
        return Wrap(
          spacing: _gap,
          runSpacing: _gap,
          children: [
            for (final (i, picture) in pictures.indexed)
              SizedBox.square(
                dimension: side,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: Image.file(
                        File(picture.path),
                        fit: BoxFit.cover,
                        cacheWidth:
                            (side * MediaQuery.devicePixelRatioOf(context))
                                .round(),
                        errorBuilder: (_, _, _) => ColoredBox(
                          color: theme.colorScheme.surfaceContainerHighest,
                          child: const Icon(Icons.broken_image_outlined),
                        ),
                      ),
                    ),
                    if (i == uploading)
                      const ColoredBox(
                        color: Colors.black38,
                        child: Center(child: CircularProgressIndicator()),
                      ),
                    if (enabled)
                      Positioned(
                        top: 2,
                        right: 2,
                        child: IconButton.filledTonal(
                          onPressed: () => onRemove(i),
                          tooltip: '移除',
                          visualDensity: VisualDensity.compact,
                          iconSize: 16,
                          icon: const Icon(Icons.close),
                        ),
                      ),
                  ],
                ),
              ),
            if (pictures.length < maxDraftPictures)
              SizedBox.square(
                dimension: side,
                child: Material(
                  color: theme.colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(4),
                  clipBehavior: Clip.antiAlias,
                  child: InkWell(
                    onTap: enabled ? onAdd : null,
                    child: Center(
                      child: Icon(
                        Icons.add_photo_alternate_outlined,
                        semanticLabel: '添加图片',
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _VisibilityButton extends StatelessWidget {
  const _VisibilityButton({required this.value, required this.onChanged});

  final StatusVisibility value;
  final ValueChanged<StatusVisibility>? onChanged;

  static IconData _iconOf(StatusVisibility v) => switch (v) {
    StatusVisibility.public => Icons.public,
    StatusVisibility.friends => Icons.group_outlined,
    StatusVisibility.onlyMe => Icons.lock_outline,
    StatusVisibility.fans => Icons.people_outline,
  };

  @override
  Widget build(BuildContext context) {
    final onChanged = this.onChanged;
    return MenuAnchor(
      menuChildren: [
        for (final v in StatusVisibility.choices)
          MenuItemButton(
            leadingIcon: Icon(_iconOf(v)),
            trailingIcon: v == value ? const Icon(Icons.check) : null,
            onPressed: () => onChanged?.call(v),
            child: Text(v.label),
          ),
      ],
      builder: (context, menu, _) => OutlinedButton.icon(
        onPressed: onChanged == null
            ? null
            : () => menu.isOpen ? menu.close() : menu.open(),
        icon: Icon(_iconOf(value), size: 18),
        label: Text(value.label),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/settings/app_settings.dart';
import '../../data/models/comment.dart';
import '../../data/models/status.dart';
import '../../data/models/weibo_user.dart';
import '../../widgets/adaptive_layout.dart';
import '../../widgets/status_card/status_card.dart';
import '../comments/comment_tile.dart';

/// Picks the text size on a slider, under a sample post and comment that
/// show it. A new size applies at once, here as everywhere, so the samples
/// are the app itself at that size.
class TextSizePage extends ConsumerWidget {
  const TextSizePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final size = ref.watch(settingsProvider.select((s) => s.textSize));
    final now = DateTime.now();
    final status = Status(
      id: 'text-size-sample',
      user: const WeiboUser(id: 'text-size-sample', screenName: 'maxbo'),
      createdAt: now.subtract(const Duration(minutes: 2)),
      text:
          '拖动下面的滑块调整字体大小，这条微博和下面的评论会按选中的大小显示。'
          '调整后所有页面的文字都会跟着变大或变小。',
      repostsCount: 3,
      commentsCount: 12,
      likesCount: 46,
    );
    final comment = Comment(
      id: 'text-size-sample',
      user: const WeiboUser(id: 'text-size-sample-2', screenName: '示例用户'),
      createdAt: now.subtract(const Duration(minutes: 1)),
      text: '「标准」就是系统设置里的字体大小。',
      likesCount: 5,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('字体大小')),
      body: Column(
        children: [
          Expanded(
            child: ReadingList(
              builder: (context, padding) => ListView(
                padding: padding,
                // Samples only: their like buttons and links do nothing.
                children: [
                  IgnorePointer(child: StatusCard(status)),
                  const Divider(height: 1),
                  IgnorePointer(child: CommentTile(comment)),
                ],
              ),
            ),
          ),
          _SizeSlider(
            size: size,
            onChanged: (chosen) => ref
                .read(settingsProvider.notifier)
                .update((s) => s.copyWith(textSize: chosen)),
          ),
        ],
      ),
    );
  }
}

/// The sizes from smallest to largest on a slider, with a step button at
/// either end.
class _SizeSlider extends StatelessWidget {
  const _SizeSlider({required this.size, required this.onChanged});

  final TextSize size;
  final ValueChanged<TextSize> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    const sizes = TextSize.values;
    void choose(int index) {
      if (index != size.index) onChanged(sizes[index]);
    }

    // Glyphs of fixed sizes, hinting at small and large text, rather than
    // text that grows and shrinks with the slider.
    Widget step(String tooltip, double fontSize, int to) => IconButton(
      tooltip: tooltip,
      onPressed: to < 0 || to >= sizes.length ? null : () => choose(to),
      // Coloured as the button colours icons, dimmed at either end.
      icon: Builder(
        builder: (context) => Text(
          'A',
          textScaler: TextScaler.noScaling,
          style: TextStyle(
            fontSize: fontSize,
            fontWeight: FontWeight.w500,
            color: IconTheme.of(context).color,
          ),
        ),
      ),
    );

    return Material(
      color: theme.colorScheme.surfaceContainer,
      child: SafeArea(
        top: false,
        child: Center(
          heightFactor: 1,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: readingWidth),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8, 16, 8, 12),
              // The label above the slider, so that its growing and
              // shrinking leaves the slider where the finger is.
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(size.label, style: theme.textTheme.titleSmall),
                  Row(
                    children: [
                      step('调小', 14, size.index - 1),
                      Expanded(
                        child: Slider(
                          value: size.index.toDouble(),
                          max: (sizes.length - 1).toDouble(),
                          divisions: sizes.length - 1,
                          semanticFormatterCallback: (value) =>
                              sizes[value.round()].label,
                          onChanged: (value) => choose(value.round()),
                        ),
                      ),
                      step('调大', 24, size.index + 1),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

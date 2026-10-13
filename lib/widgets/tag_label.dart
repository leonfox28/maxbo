import 'package:flutter/material.dart';

/// A small label such as 广告 or 置顶: outlined, or filled with [color].
class TagLabel extends StatelessWidget {
  const TagLabel(this.text, {super.key, this.color});

  final String text;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = this.color;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color,
        border: color == null
            ? Border.all(color: theme.colorScheme.outline)
            : null,
        borderRadius: BorderRadius.circular(3),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
        child: Text(
          text,
          style: theme.textTheme.labelSmall?.copyWith(
            color: color == null
                ? theme.colorScheme.onSurfaceVariant
                : Colors.white,
          ),
        ),
      ),
    );
  }
}

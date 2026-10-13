import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import '../../core/media/weibo_image.dart';
import '../../data/models/status.dart';
import 'status_text_parser.dart';

/// The body of a status: text with tappable links, topics, mentions and
/// inline emoticons.
class StatusText extends StatefulWidget {
  const StatusText(
    this.text, {
    super.key,
    this.urls = const [],
    this.author,
    this.style,
    this.onLink,
    this.onExpand,
  });

  /// The status `text` HTML.
  final String text;
  final List<UrlEntity> urls;

  /// Shown as `@author：` before the text, for retweeted posts.
  final String? author;
  final TextStyle? style;
  final ValueChanged<StatusLink>? onLink;

  /// Called when "展开" at the end of a truncated long post is tapped.
  final VoidCallback? onExpand;

  @override
  State<StatusText> createState() => _StatusTextState();
}

class _StatusTextState extends State<StatusText> {
  late List<TextSegment> _segments;

  // One per tappable segment, by index; rebuilt only when the text changes.
  final _recognizers = <int, TapGestureRecognizer>{};

  @override
  void initState() {
    super.initState();
    _parse();
  }

  @override
  void didUpdateWidget(StatusText oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.text != oldWidget.text ||
        widget.urls != oldWidget.urls ||
        widget.author != oldWidget.author) {
      _parse();
    }
  }

  @override
  void dispose() {
    _disposeRecognizers();
    super.dispose();
  }

  void _parse() {
    _disposeRecognizers();
    final author = widget.author;
    _segments = [
      if (author != null) ...[
        LinkSegment('@$author', MentionLink(author)),
        const PlainSegment('：'),
      ],
      ...parseStatusText(widget.text, urls: widget.urls),
    ];
    for (final (i, segment) in _segments.indexed) {
      // Callbacks read `widget` at tap time, so they never go stale.
      final onTap = switch (segment) {
        LinkSegment(:final link) => () => widget.onLink?.call(link),
        ExpandSegment() => () => widget.onExpand?.call(),
        _ => null,
      };
      if (onTap != null) {
        _recognizers[i] = TapGestureRecognizer()..onTap = onTap;
      }
    }
  }

  void _disposeRecognizers() {
    for (final r in _recognizers.values) {
      r.dispose();
    }
    _recognizers.clear();
  }

  @override
  Widget build(BuildContext context) {
    final base = DefaultTextStyle.of(context).style.merge(widget.style);
    final linkStyle = TextStyle(color: Theme.of(context).colorScheme.primary);
    final iconSize = (base.fontSize ?? 14) * 1.2;

    Widget inlineImage(String url, String label) => Semantics(
      label: label,
      child: WeiboImage(
        url,
        width: iconSize,
        height: iconSize,
        fit: BoxFit.contain,
        placeholder: false,
      ),
    );

    return Text.rich(
      TextSpan(
        style: base,
        children: [
          for (final (i, segment) in _segments.indexed)
            switch (segment) {
              PlainSegment(:final text) => TextSpan(text: text),
              EmoticonSegment(:final url, :final alt) => WidgetSpan(
                alignment: PlaceholderAlignment.middle,
                child: inlineImage(url, alt),
              ),
              // Hit testing finds the leaf span, so the recognizer goes on
              // the text itself, not on a parent span.
              LinkSegment(:final text, :final iconUrl) => TextSpan(
                style: linkStyle,
                children: [
                  if (iconUrl != null)
                    WidgetSpan(
                      alignment: PlaceholderAlignment.middle,
                      child: GestureDetector(
                        onTap: _recognizers[i]?.onTap,
                        child: inlineImage(iconUrl, ''),
                      ),
                    ),
                  TextSpan(text: text, recognizer: _recognizers[i]),
                ],
              ),
              ExpandSegment() => TextSpan(
                text: '展开',
                style: linkStyle,
                recognizer: _recognizers[i],
              ),
            },
        ],
      ),
    );
  }
}

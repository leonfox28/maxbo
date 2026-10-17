import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Material 3's window size classes, by the width of the app's window. The
/// window, not the screen: the app may share the screen with another, and a
/// foldable changes size as it folds and unfolds.
enum WindowSize {
  compact,
  medium,
  expanded;

  static WindowSize of(BuildContext context) =>
      fromWidth(MediaQuery.sizeOf(context).width);

  static WindowSize fromWidth(double width) => width < 600
      ? compact
      : width < 840
      ? medium
      : expanded;
}

/// The widest a page's content runs. Wider windows (tablets, unfolded
/// foldables, phones on their side) show it as a column of this width in
/// their middle, much as a phone shows it, rather than stretching lines,
/// pictures and videos across the window.
const readingWidth = 640.0;

/// The widest a timeline of status cards runs, on a window at least
/// [wideCardFrom] wide; narrower windows keep it to [readingWidth]. Cards
/// that wide put their media beside their text (`StatusCard`), so lines
/// stay as short as in the reading column.
const feedWidth = 1000.0;

/// The width from which a status card puts its media beside its text.
const wideCardFrom = 880.0;

/// The column a timeline of status cards takes in a window [width] wide.
double feedColumnWidth(double width) =>
    width >= wideCardFrom ? feedWidth : readingWidth;

/// The side margins that centre a [column] wide column in [width].
EdgeInsets readingInsets(double width, {double column = readingWidth}) =>
    EdgeInsets.symmetric(horizontal: math.max(0, (width - column) / 2));

/// Builds a page's vertical [ListView] with the padding that keeps its items
/// to the reading column. Margins rather than a narrower list, so the user
/// can drag the list anywhere across the window.
///
/// The padding also has the top and bottom [MediaQuery] padding, which a
/// list without padding of its own would take, and then [padding].
class ReadingList extends StatelessWidget {
  const ReadingList({
    super.key,
    this.padding = EdgeInsets.zero,
    this.feed = false,
    required this.builder,
  });

  final EdgeInsets padding;

  /// A timeline of status cards, in a [feedColumnWidth] column.
  final bool feed;
  final Widget Function(BuildContext context, EdgeInsets padding) builder;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.paddingOf(context);
    return LayoutBuilder(
      builder: (context, constraints) => builder(
        context,
        readingInsets(
              constraints.maxWidth,
              column: feed
                  ? feedColumnWidth(constraints.maxWidth)
                  : readingWidth,
            ) +
            EdgeInsets.only(top: media.top, bottom: media.bottom) +
            padding,
      ),
    );
  }
}

/// [slivers] kept to the reading column, for a [CustomScrollView]. As with
/// [ReadingList], the scroll view itself spans the window.
class ReadingSlivers extends StatelessWidget {
  const ReadingSlivers({super.key, required this.slivers});

  final List<Widget> slivers;

  @override
  Widget build(BuildContext context) {
    return SliverLayoutBuilder(
      builder: (context, constraints) => SliverPadding(
        padding: readingInsets(constraints.crossAxisExtent),
        sliver: SliverMainAxisGroup(slivers: slivers),
      ),
    );
  }
}

/// Two columns side by side, as wide as reading columns can be and centred
/// together in a wider window, split by a line unless not [divider].
///
/// [crossAxisAlignment] as in a [Row]: stretched by default, filling the
/// height given, as panes of a page do.
class ReadingPanes extends StatelessWidget {
  const ReadingPanes({
    super.key,
    this.start,
    this.end,
    this.divider = true,
    this.crossAxisAlignment = CrossAxisAlignment.stretch,
  });

  final Widget? start;
  final Widget? end;
  final bool divider;
  final CrossAxisAlignment crossAxisAlignment;

  @override
  Widget build(BuildContext context) {
    return Center(
      heightFactor: 1,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 2 * readingWidth + 1),
        child: Row(
          crossAxisAlignment: crossAxisAlignment,
          children: [
            Expanded(child: start ?? const SizedBox.shrink()),
            divider
                ? const VerticalDivider(width: 1, thickness: 1)
                : const SizedBox(width: 1),
            Expanded(child: end ?? const SizedBox.shrink()),
          ],
        ),
      ),
    );
  }
}

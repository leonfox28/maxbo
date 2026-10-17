import 'package:flutter/material.dart';

import 'adaptive_layout.dart';

/// A bar floating over the bottom of a page, such as the tab bar or the
/// comment bar. Styled as the feed is: a neutral surface, set off by an
/// outline and a shadow rather than by colour, leaving the primary colour to
/// mark what's active (as it does links and likes).
///
/// Its [child] sits 8 in from every edge, so a round item at either end is
/// as far from the pill's ends as from its top and bottom.
class FloatingPill extends StatelessWidget {
  const FloatingPill({
    super.key,
    this.stretch = false,
    this.alignment = AlignmentDirectional.center,
    required this.child,
  });

  /// Spans the page, less a margin, rather than wrapping [child]. On a wide
  /// window, it spans the reading column instead.
  final bool stretch;

  /// Where a pill that wraps [child] sits across the page, kept as far in
  /// from the side as a stretched one.
  final AlignmentGeometry alignment;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final pill = Material(
      color: colors.surfaceContainerHigh,
      shape: StadiumBorder(side: BorderSide(color: colors.outlineVariant)),
      elevation: 3,
      shadowColor: colors.shadow,
      child: Padding(padding: const EdgeInsets.all(8), child: child),
    );
    return SafeArea(
      top: false,
      minimum: const EdgeInsets.only(bottom: 12),
      child: stretch
          ? Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Center(
                heightFactor: 1,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: readingWidth - 32,
                  ),
                  child: pill,
                ),
              ),
            )
          : Align(
              alignment: alignment,
              heightFactor: 1,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: pill,
              ),
            ),
    );
  }
}

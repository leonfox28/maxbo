import 'package:flutter/material.dart';

import 'bars_on_scroll.dart';

/// Tells the pages in a home tab when its button is tapped while the tab is
/// already open.
class TabReselect extends InheritedWidget {
  const TabReselect({super.key, required this.taps, required super.child});

  final Listenable taps;

  static Listenable? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<TabReselect>()?.taps;

  @override
  bool updateShouldNotify(TabReselect oldWidget) => taps != oldWidget.taps;
}

/// A [RefreshIndicator] for a tab's list. Tapping the open tab again
/// scrolls the list back to its top, or refreshes it if it's there already.
/// The list must be the page's primary scroll view (`primary: true`), so
/// that a tap on the iOS status bar scrolls it too.
class TabRefreshIndicator extends StatefulWidget {
  const TabRefreshIndicator({
    super.key,
    required this.onRefresh,
    required this.child,
  });

  final RefreshCallback onRefresh;
  final Widget child;

  @override
  State<TabRefreshIndicator> createState() => _TabRefreshIndicatorState();
}

class _TabRefreshIndicatorState extends State<TabRefreshIndicator> {
  final _indicator = GlobalKey<RefreshIndicatorState>();
  Listenable? _taps;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final taps = TabReselect.maybeOf(context);
    if (taps == _taps) return;
    _taps?.removeListener(_onTap);
    _taps = taps?..addListener(_onTap);
  }

  @override
  void dispose() {
    _taps?.removeListener(_onTap);
    super.dispose();
  }

  void _onTap() {
    final controller = PrimaryScrollController.maybeOf(context);
    if (controller == null || !controller.hasClients) return;
    final position = controller.position;
    if (position.pixels > position.minScrollExtent) {
      controller.animateTo(
        position.minScrollExtent,
        duration: const Duration(milliseconds: 400),
        curve: BarsOnScroll.curve,
      );
    } else {
      // Shows the spinner as a pull would; ignored while one is running.
      _indicator.currentState?.show();
    }
  }

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      key: _indicator,
      onRefresh: widget.onRefresh,
      child: widget.child,
    );
  }
}

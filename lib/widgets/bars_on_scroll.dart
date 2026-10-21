import 'package:flutter/material.dart';

/// Whether bars that give way to reading should show: they hide while the
/// user scrolls down a list and come back when they scroll up or reach its
/// top. The owner feeds it the list's [ScrollUpdateNotification]s and
/// rebuilds when [onScroll] says [visible] changed.
class BarsOnScroll {
  var visible = true;

  /// How far the list has scrolled since it last turned: positive down.
  var _scrolled = 0.0;

  /// Scrolling this far one way shows or hides the bars. Lifting a finger
  /// from a fling can report a few pixels the other way, which mustn't
  /// bring them back.
  static const _threshold = 24.0;

  static const duration = Duration(milliseconds: 250);
  static const curve = Curves.easeOutCubic;

  /// Takes in a scroll; true if [visible] changed.
  bool onScroll(ScrollUpdateNotification n) {
    final delta = n.scrollDelta ?? 0;
    // Only the user's own dragging counts. Hiding a bar can make the list
    // taller and pull it back from its end, which mustn't show the bar.
    if (n.dragDetails == null ||
        n.metrics.axis != Axis.vertical ||
        delta == 0) {
      return false;
    }
    if ((delta > 0) != (_scrolled > 0)) _scrolled = 0;
    _scrolled += delta;
    final show = n.metrics.pixels <= 0
        ? true
        : _scrolled.abs() < _threshold
        ? visible
        : _scrolled < 0;
    if (show == visible) return false;
    visible = show;
    return true;
  }

  void show() {
    visible = true;
    _scrolled = 0;
  }
}

/// An app bar that folds away while [visible] is false. It sits above the
/// list it gives way to, never over it, so goes in a column with it.
class HidingAppBar extends StatelessWidget {
  const HidingAppBar({
    super.key,
    required this.visible,
    required this.title,
    this.actions,
  });

  final bool visible;
  final Widget title;
  final List<Widget>? actions;

  @override
  Widget build(BuildContext context) {
    return ClipRect(
      child: AnimatedAlign(
        alignment: Alignment.bottomCenter,
        heightFactor: visible ? 1 : 0,
        duration: BarsOnScroll.duration,
        curve: BarsOnScroll.curve,
        child: AppBar(
          title: title,
          actions: actions,
          primary: false,
          // The list scrolls under nothing, so the bar never tints.
          notificationPredicate: (_) => false,
        ),
      ),
    );
  }
}

/// [body] under a [HidingAppBar] of [title] and [actions], which gives way
/// while the user reads down the body, as the home tab bar does. For a
/// scaffold without an app bar of its own.
class HidingAppBarBody extends StatefulWidget {
  const HidingAppBarBody({
    super.key,
    required this.title,
    this.actions,
    required this.body,
  });

  final Widget title;
  final List<Widget>? actions;
  final Widget body;

  @override
  State<HidingAppBarBody> createState() => _HidingAppBarBodyState();
}

class _HidingAppBarBodyState extends State<HidingAppBarBody> {
  final _bars = BarsOnScroll();

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Column(
        children: [
          HidingAppBar(
            visible: _bars.visible,
            title: widget.title,
            actions: widget.actions,
          ),
          Expanded(
            child: NotificationListener<ScrollUpdateNotification>(
              onNotification: (n) {
                if (_bars.onScroll(n)) setState(() {});
                return false;
              },
              child: widget.body,
            ),
          ),
        ],
      ),
    );
  }
}

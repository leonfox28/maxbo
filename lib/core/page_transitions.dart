import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

/// Flutter's own transitions, with Android's predictive back made smooth.
const pageTransitionsTheme = PageTransitionsTheme(
  builders: {TargetPlatform.android: _PredictiveBackTransitions()},
);

/// [PredictiveBackPageTransitionsBuilder], Flutter's default on Android, made
/// to follow the back gesture at the screen's full frame rate.
///
/// Measured on the Android 16 emulator, the stock transition ran at half
/// the frame rate while the finger was down. The gesture's progress arrives
/// once a frame, just after the vsync: when it gets in before the frame
/// starts, it is drawn in that frame and asks for no other, so the next
/// progress has to wait a whole frame more. And as the page shrinks, its
/// text was rasterized again at every new scale, most of each frame's work.
///
/// So while the gesture lasts, a ticker asks for every frame, and the page
/// and the one it reveals are each drawn once, then moved as pictures, as
/// Android does with its own windows. Anything moving on them stands still
/// until the gesture ends.
class _PredictiveBackTransitions extends PageTransitionsBuilder {
  const _PredictiveBackTransitions();

  static const _transitions = PredictiveBackPageTransitionsBuilder();

  @override
  Duration get transitionDuration => _transitions.transitionDuration;

  @override
  Duration get reverseTransitionDuration =>
      _transitions.reverseTransitionDuration;

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) => _transitions.buildTransitions(
    route,
    context,
    animation,
    secondaryAnimation,
    switch (route.navigator) {
      final navigator? => _BackGesture(
        inProgress: navigator.userGestureInProgressNotifier,
        child: child,
      ),
      null => child,
    },
  );
}

class _BackGesture extends StatefulWidget {
  const _BackGesture({required this.inProgress, required this.child});

  /// The navigator's, which also counts the time the page takes to settle
  /// after the finger lifts. Listened to rather than read as the route
  /// builds: a gesture called off with the page back where it started ends
  /// without the route building again.
  final ValueListenable<bool> inProgress;
  final Widget child;

  @override
  State<_BackGesture> createState() => _BackGestureState();
}

class _BackGestureState extends State<_BackGesture>
    with SingleTickerProviderStateMixin {
  late final Ticker _frames = createTicker((_) {});
  final _snapshot = SnapshotController();

  @override
  void initState() {
    super.initState();
    widget.inProgress.addListener(_update);
    _update();
  }

  @override
  void didUpdateWidget(_BackGesture oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.inProgress != oldWidget.inProgress) {
      oldWidget.inProgress.removeListener(_update);
      widget.inProgress.addListener(_update);
      _update();
    }
  }

  void _update() {
    final inProgress = widget.inProgress.value;
    _snapshot.allowSnapshotting = inProgress;
    if (!inProgress) {
      _frames.stop();
    } else if (!_frames.isActive) {
      _frames.start();
    }
  }

  @override
  void dispose() {
    widget.inProgress.removeListener(_update);
    _frames.dispose();
    _snapshot.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => SnapshotWidget(
    controller: _snapshot,
    // A page showing a web page is drawn as it is.
    mode: SnapshotMode.permissive,
    child: widget.child,
  );
}

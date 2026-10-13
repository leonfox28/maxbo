import 'dart:async';
import 'dart:collection';
import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

final requestThrottleProvider = Provider<RequestThrottle>(
  (ref) => RequestThrottle(),
);

/// Caps concurrent requests and spaces out their starts, so the app never
/// sends Weibo a burst; backs off further when Weibo signals rate limiting.
class RequestThrottle {
  RequestThrottle({
    this.maxConcurrent = 2,
    this.minInterval = const Duration(milliseconds: 500),
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  static const _firstBackOff = Duration(seconds: 5);
  static const _maxBackOff = Duration(seconds: 60);

  final int maxConcurrent;
  final Duration minInterval;
  final DateTime Function() _now;

  final _waiters = Queue<Completer<void>>();
  int _active = 0;
  DateTime? _nextStart;
  DateTime? _backOffUntil;
  Duration _backOff = Duration.zero;

  /// Waits for a free slot and the next allowed start time.
  ///
  /// Every call must be paired with exactly one [release].
  Future<void> acquire() async {
    if (_active < maxConcurrent) {
      _active++;
    } else {
      final waiter = Completer<void>();
      _waiters.add(waiter);
      await waiter.future; // release() hands its slot straight to us.
    }

    final now = _now();
    var start = now;
    for (final earliest in [_nextStart, _backOffUntil]) {
      if (earliest != null && earliest.isAfter(start)) start = earliest;
    }
    _nextStart = start.add(minInterval);
    if (start.isAfter(now)) await Future<void>.delayed(start.difference(now));
  }

  void release() {
    if (_waiters.isNotEmpty) {
      _waiters.removeFirst().complete();
    } else {
      _active--;
    }
  }

  /// Delays later requests, doubling the pause on each consecutive call.
  void backOff() {
    _backOff = _backOff == Duration.zero
        ? _firstBackOff
        : Duration(
            milliseconds: min(
              _backOff.inMilliseconds * 2,
              _maxBackOff.inMilliseconds,
            ),
          );
    _backOffUntil = _now().add(_backOff);
  }

  void resetBackOff() => _backOff = Duration.zero;
}

/// The app-wide Riverpod retry policy: never retry on our own.
///
/// Riverpod retries failed providers by default, but requests to Weibo must
/// only follow user actions (PLAN §2), so retrying is left to the user.
Duration? noRetry(int retryCount, Object error) => null;

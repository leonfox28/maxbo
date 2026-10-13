import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';

void main() {
  RequestThrottle throttleFor(FakeAsync async) {
    final start = DateTime(2026);
    return RequestThrottle(now: () => start.add(async.elapsed));
  }

  test('spaces out request starts', () {
    fakeAsync((async) {
      final throttle = throttleFor(async);
      final started = <Duration>[];
      for (var i = 0; i < 2; i++) {
        throttle.acquire().then((_) => started.add(async.elapsed));
      }

      async.elapse(const Duration(seconds: 1));

      expect(started, [Duration.zero, const Duration(milliseconds: 500)]);
    });
  });

  test('caps concurrency and hands freed slots to waiters in order', () {
    fakeAsync((async) {
      final throttle = throttleFor(async);
      final started = <int>[];
      for (var i = 0; i < 3; i++) {
        throttle.acquire().then((_) => started.add(i));
      }

      async.elapse(const Duration(seconds: 5));
      expect(started, [0, 1]);

      throttle.release();
      async.elapse(const Duration(seconds: 1));
      expect(started, [0, 1, 2]);
    });
  });

  test('backs off exponentially and resets on success', () {
    fakeAsync((async) {
      final throttle = throttleFor(async);
      Duration? startedAt;

      throttle.backOff();
      throttle.acquire().then((_) => startedAt = async.elapsed);
      async.elapse(const Duration(seconds: 4));
      expect(startedAt, isNull);
      async.elapse(const Duration(seconds: 1));
      expect(startedAt, const Duration(seconds: 5));
      throttle.release();

      throttle.backOff();
      final second = async.elapsed;
      startedAt = null;
      throttle.acquire().then((_) => startedAt = async.elapsed);
      async.elapse(const Duration(seconds: 20));
      expect(startedAt! - second, const Duration(seconds: 10));
      throttle.release();

      throttle
        ..resetBackOff()
        ..backOff();
      final third = async.elapsed;
      startedAt = null;
      throttle.acquire().then((_) => startedAt = async.elapsed);
      async.elapse(const Duration(seconds: 20));
      expect(startedAt! - third, const Duration(seconds: 5));
    });
  });
}

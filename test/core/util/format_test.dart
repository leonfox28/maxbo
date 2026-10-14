import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/util/format.dart';

void main() {
  group('parseWeiboDate', () {
    test('reads the offset into UTC', () {
      expect(
        parseWeiboDate('Mon Oct 12 13:17:46 +0800 2026'),
        DateTime.utc(2026, 10, 12, 5, 17, 46),
      );
      expect(
        parseWeiboDate('Fri Jan 2 23:00:00 -0130 2026'),
        DateTime.utc(2026, 1, 3, 0, 30),
      );
    });

    test('returns null for other formats', () {
      expect(parseWeiboDate(null), isNull);
      expect(parseWeiboDate('2026-10-12 13:17'), isNull);
      expect(parseWeiboDate('Mon Foo 12 13:17:46 +0800 2026'), isNull);
    });
  });

  group('formatPostTime', () {
    final now = DateTime(2026, 10, 13, 15, 30);
    String fmt(DateTime t) => formatPostTime(t, now: now);

    test('is relative within today', () {
      expect(fmt(now.subtract(const Duration(seconds: 20))), '刚刚');
      expect(fmt(now.add(const Duration(minutes: 2))), '刚刚');
      expect(fmt(now.subtract(const Duration(minutes: 5))), '5分钟前');
      expect(fmt(DateTime(2026, 10, 13, 0, 10)), '15小时前');
    });

    test('names yesterday, then dates', () {
      expect(fmt(DateTime(2026, 10, 12, 23, 59)), '昨天 23:59');
      expect(fmt(DateTime(2026, 10, 11, 8, 5)), '10-11 08:05');
      expect(fmt(DateTime(2025, 12, 31, 8, 5)), '2025-12-31');
    });
  });

  test('formatCount rounds down into 万 and 亿', () {
    expect(formatCount(0), '0');
    expect(formatCount(9999), '9999');
    expect(formatCount(10000), '1万');
    expect(formatCount(12999), '1.2万');
    expect(formatCount(1234567), '123万');
    expect(formatCount(99999999), '9999万');
    expect(formatCount(150000000), '1.5亿');
  });

  test('formatDuration', () {
    expect(formatDuration(5), '0:05');
    expect(formatDuration(754), '12:34');
    expect(formatDuration(3725), '1:02:05');
  });

  test('formatBytes', () {
    const mb = 1024 * 1024;
    expect(formatBytes(0), '0 MB');
    expect(formatBytes(mb * 5 ~/ 2), '2.5 MB');
    expect(formatBytes(mb * 123 + 1000), '123 MB');
    expect(formatBytes(300 * mb), '300 MB');
    expect(formatBytes(1024 * mb), '1 GB');
    expect(formatBytes(1536 * mb), '1.5 GB');
  });
}

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/media/image_disk_cache.dart';

void main() {
  late Directory dir;

  setUp(() => dir = Directory.systemTemp.createTempSync('cacheimage'));
  tearDown(() => dir.deleteSync(recursive: true));

  void write(String name, int bytes, DateTime modified) => (File(
    '${dir.path}/$name',
  )..writeAsBytesSync(List.filled(bytes, 0))).setLastModifiedSync(modified);

  List<String> names() =>
      [for (final f in dir.listSync()) f.uri.pathSegments.last]..sort();

  test('deletes the oldest files until under the limit', () async {
    write('old', 400, DateTime(2026, 1, 1));
    write('mid', 400, DateTime(2026, 2, 1));
    write('new', 400, DateTime(2026, 3, 1));
    expect(await directorySize(dir), 1200);

    await trimDirectory(dir, 900);

    expect(names(), ['mid', 'new']);
    expect(await directorySize(dir), 800);
  });

  test('keeps everything under the limit, and copes with no folder', () async {
    write('a', 100, DateTime(2026, 1, 1));
    await trimDirectory(dir, 100);
    expect(names(), ['a']);

    final missing = Directory('${dir.path}/missing');
    await trimDirectory(missing, 0);
    expect(await directorySize(missing), 0);
  });
}

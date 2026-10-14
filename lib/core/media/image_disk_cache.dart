import 'dart:io';

import 'package:extended_image/extended_image.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';

final imageDiskCacheProvider = Provider<ImageDiskCache>(
  (ref) => const ImageDiskCache(),
);

/// The folder where `extended_image` keeps downloaded pictures, one file
/// per picture, which it never trims by itself.
class ImageDiskCache {
  const ImageDiskCache();

  Future<Directory> _directory() async => Directory(
    '${(await getTemporaryDirectory()).path}/$cacheImageFolderName',
  );

  Future<int> sizeBytes() async => directorySize(await _directory());

  /// Deletes every cached picture, on disk and in memory.
  Future<void> clear() async {
    await clearDiskCachedImages();
    clearMemoryImageCache();
  }

  /// Deletes the oldest pictures until the cache is at most [maxBytes].
  Future<void> trim(int maxBytes) async =>
      trimDirectory(await _directory(), maxBytes);
}

Future<int> directorySize(Directory dir) async {
  if (!await dir.exists()) return 0;
  var size = 0;
  await for (final entity in dir.list()) {
    if (entity is File) size += await entity.length();
  }
  return size;
}

/// Deletes files in [dir], least recently written first, until the rest
/// add up to at most [maxBytes].
Future<void> trimDirectory(Directory dir, int maxBytes) async {
  if (!await dir.exists()) return;
  final files = <(File, FileStat)>[];
  var total = 0;
  await for (final entity in dir.list()) {
    if (entity is! File) continue;
    final stat = await entity.stat();
    files.add((entity, stat));
    total += stat.size;
  }
  if (total <= maxBytes) return;
  files.sort((a, b) => a.$2.modified.compareTo(b.$2.modified));
  for (final (file, stat) in files) {
    if (total <= maxBytes) break;
    try {
      await file.delete();
      total -= stat.size;
    } on FileSystemException {
      // Being written or already gone; the next trim gets it.
    }
  }
}

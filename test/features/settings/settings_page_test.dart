import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/media/image_disk_cache.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/settings/app_settings.dart';
import 'package:maxbo/features/settings/settings_page.dart';

import '../../support/fakes.dart';

void main() {
  late InMemorySettingsStorage storage;
  late FakeImageDiskCache cache;

  Future<void> pumpPage(WidgetTester tester) async {
    storage = InMemorySettingsStorage();
    cache = FakeImageDiskCache(450 * 1024 * 1024);
    await tester.pumpWidget(
      ProviderScope(
        retry: noRetry,
        overrides: [
          settingsStorageProvider.overrideWithValue(storage),
          imageDiskCacheProvider.overrideWithValue(cache),
        ],
        child: const MaterialApp(home: SettingsPage()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('shows the current settings and cache size', (tester) async {
    await pumpPage(tester);

    expect(find.text('跟随系统'), findsOneWidget);
    expect(find.text('标准'), findsOneWidget);
    expect(find.text('300 MB，超出后先删最早下载的图片'), findsOneWidget);
    expect(find.text('已用 450 MB'), findsOneWidget);
  });

  testWidgets('picks a theme and saves it', (tester) async {
    await pumpPage(tester);

    await tester.tap(find.text('主题'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('深色'));
    await tester.pumpAndSettle();

    expect(storage.stored.themeMode, ThemeMode.dark);
    expect(find.text('深色'), findsOneWidget);
    expect(find.byType(SimpleDialog), findsNothing);
  });

  testWidgets('picks an image quality', (tester) async {
    await pumpPage(tester);

    await tester.tap(find.text('图片质量'));
    await tester.pumpAndSettle();
    expect(find.text(ImageQuality.original.description), findsOneWidget);
    await tester.tap(find.text('省流量'));
    await tester.pumpAndSettle();

    expect(storage.stored.imageQuality, ImageQuality.dataSaver);
  });

  testWidgets('lowering the cache limit trims the cache', (tester) async {
    await pumpPage(tester);

    await tester.tap(find.text('图片缓存上限'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('100 MB'));
    await tester.pumpAndSettle();

    expect(storage.stored.imageCacheLimitMb, 100);
    expect(cache.trims, [100 * 1024 * 1024]);
    expect(find.text('已用 100 MB'), findsOneWidget);
  });

  testWidgets('clears the cache after confirming', (tester) async {
    await pumpPage(tester);

    await tester.tap(find.text('清除图片缓存'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(cache.clears, 0);

    await tester.tap(find.text('清除图片缓存'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('清除'));
    await tester.pumpAndSettle();

    expect(cache.clears, 1);
    expect(find.text('已用 0 MB'), findsOneWidget);
    expect(find.text('图片缓存已清除'), findsOneWidget);
  });
}

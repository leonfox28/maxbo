import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/settings/app_settings.dart';
import 'package:maxbo/data/models/pic_info.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

void main() {
  group('ImageQuality', () {
    final pic = PicInfo.tryFromJson(picJson('p', width: 3000, height: 2000))!;

    test('saves data in lists by ignoring the pixel ratio', () {
      expect(ImageQuality.standard.listPixels(120, 3), 360);
      expect(ImageQuality.original.listPixels(120, 3), 360);
      expect(ImageQuality.dataSaver.listPixels(120, 3), 120);
      expect(
        pic.variantFor(ImageQuality.dataSaver.listPixels(120, 3)).url,
        contains('/wap180/'),
      );
    });

    test('shows the original in the viewer only when asked to', () {
      expect(
        ImageQuality.standard.viewerVariant(pic).url,
        contains('/mw2000/'),
      );
      expect(
        ImageQuality.dataSaver.viewerVariant(pic).url,
        contains('/mw2000/'),
      );
      expect(ImageQuality.original.viewerVariant(pic).url, contains('/large/'));
    });
  });

  test('starts from the loaded settings and saves every change', () async {
    final storage = InMemorySettingsStorage();
    final container = ProviderContainer(
      retry: noRetry,
      overrides: [
        initialSettingsProvider.overrideWithValue(
          const AppSettings(themeMode: ThemeMode.dark),
        ),
        settingsStorageProvider.overrideWithValue(storage),
      ],
    );
    addTearDown(container.dispose);

    expect(container.read(settingsProvider).themeMode, ThemeMode.dark);
    await container
        .read(settingsProvider.notifier)
        .update((s) => s.copyWith(imageQuality: ImageQuality.dataSaver));

    final settings = container.read(settingsProvider);
    expect(settings.themeMode, ThemeMode.dark);
    expect(settings.imageQuality, ImageQuality.dataSaver);
    expect(storage.saves, 1);
    expect(storage.stored.imageQuality, ImageQuality.dataSaver);
  });
}

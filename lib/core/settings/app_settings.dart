import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../data/models/pic_info.dart';

/// Which picture renditions to load.
enum ImageQuality {
  dataSaver('省流量', '列表里的图片用较小的尺寸'),
  standard('标准', '按屏幕清晰度选择尺寸'),
  original('原图', '查看大图时加载原图，可能有好几 MB');

  const ImageQuality(this.label, this.description);

  final String label;
  final String description;

  /// The physical pixels an in-list picture [logical] points across should
  /// cover, on a screen of [devicePixelRatio].
  double listPixels(double logical, double devicePixelRatio) =>
      logical * (this == dataSaver ? 1 : devicePixelRatio);

  /// The rendition the image viewer shows.
  PicVariant viewerVariant(PicInfo pic) =>
      this == original ? pic.largest ?? pic.viewerVariant : pic.viewerVariant;
}

/// User preferences, kept on the device only.
class AppSettings {
  const AppSettings({
    this.themeMode = ThemeMode.system,
    this.imageQuality = ImageQuality.standard,
    this.imageCacheLimitMb = 300,
  });

  final ThemeMode themeMode;
  final ImageQuality imageQuality;

  /// How large the picture disk cache may grow before the oldest pictures
  /// are deleted.
  final int imageCacheLimitMb;

  static const imageCacheLimitChoices = [100, 300, 500, 1024];

  AppSettings copyWith({
    ThemeMode? themeMode,
    ImageQuality? imageQuality,
    int? imageCacheLimitMb,
  }) => AppSettings(
    themeMode: themeMode ?? this.themeMode,
    imageQuality: imageQuality ?? this.imageQuality,
    imageCacheLimitMb: imageCacheLimitMb ?? this.imageCacheLimitMb,
  );
}

abstract interface class SettingsStorage {
  Future<AppSettings> load();
  Future<void> save(AppSettings settings);
}

class PrefsSettingsStorage implements SettingsStorage {
  static const _theme = 'settings.theme';
  static const _quality = 'settings.imageQuality';
  static const _cacheLimit = 'settings.imageCacheLimitMb';

  final _prefs = SharedPreferencesAsync();

  @override
  Future<AppSettings> load() async {
    const defaults = AppSettings();
    T byName<T extends Enum>(List<T> values, String? name, T fallback) =>
        values.where((v) => v.name == name).firstOrNull ?? fallback;
    final limit = await _prefs.getInt(_cacheLimit);
    return AppSettings(
      themeMode: byName(
        ThemeMode.values,
        await _prefs.getString(_theme),
        defaults.themeMode,
      ),
      imageQuality: byName(
        ImageQuality.values,
        await _prefs.getString(_quality),
        defaults.imageQuality,
      ),
      imageCacheLimitMb: AppSettings.imageCacheLimitChoices.contains(limit)
          ? limit!
          : defaults.imageCacheLimitMb,
    );
  }

  @override
  Future<void> save(AppSettings settings) async {
    await _prefs.setString(_theme, settings.themeMode.name);
    await _prefs.setString(_quality, settings.imageQuality.name);
    await _prefs.setInt(_cacheLimit, settings.imageCacheLimitMb);
  }
}

/// Overridden in `main()` with the settings loaded at launch.
final initialSettingsProvider = Provider<AppSettings>(
  (ref) => const AppSettings(),
);

final settingsStorageProvider = Provider<SettingsStorage>(
  (ref) => PrefsSettingsStorage(),
);

final settingsProvider = NotifierProvider<SettingsController, AppSettings>(
  SettingsController.new,
);

class SettingsController extends Notifier<AppSettings> {
  @override
  AppSettings build() => ref.read(initialSettingsProvider);

  /// Applies [change] at once and saves it.
  Future<void> update(AppSettings Function(AppSettings) change) {
    state = change(state);
    return ref.read(settingsStorageProvider).save(state);
  }
}

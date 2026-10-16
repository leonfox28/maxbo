import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/media/image_disk_cache.dart';
import '../../core/settings/app_settings.dart';
import '../../core/util/format.dart';
import '../../widgets/adaptive_layout.dart';

const _mb = 1024 * 1024;

/// The size of the picture disk cache; refreshed after clearing or trimming.
final imageCacheSizeProvider = FutureProvider.autoDispose<int>(
  (ref) => ref.read(imageDiskCacheProvider).sizeBytes(),
);

const _themeLabels = {
  ThemeMode.system: '跟随系统',
  ThemeMode.light: '浅色',
  ThemeMode.dark: '深色',
};

class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  Future<void> _setCacheLimit(WidgetRef ref, int mb) async {
    await ref
        .read(settingsProvider.notifier)
        .update((s) => s.copyWith(imageCacheLimitMb: mb));
    await ref.read(imageDiskCacheProvider).trim(mb * _mb);
    ref.invalidate(imageCacheSizeProvider);
  }

  Future<void> _clearCache(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('清除图片缓存'),
        content: const Text('已下载的图片会被删除，之后浏览时重新下载。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('清除'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    await ref.read(imageDiskCacheProvider).clear();
    ref.invalidate(imageCacheSizeProvider);
    if (context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('图片缓存已清除')));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    final controller = ref.read(settingsProvider.notifier);
    final cacheSize = switch (ref.watch(imageCacheSizeProvider)) {
      AsyncData(:final value) => formatBytes(value),
      AsyncError() => '无法读取',
      _ => '计算中…',
    };
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: ReadingList(
        builder: (context, padding) => ListView(
          padding: padding,
          children: [
            const _SectionHeader('外观'),
            ListTile(
              leading: const Icon(Icons.dark_mode_outlined),
              title: const Text('主题'),
              subtitle: Text(_themeLabels[settings.themeMode]!),
              onTap: () async {
                final mode = await _choose(
                  context,
                  title: '主题',
                  selected: settings.themeMode,
                  options: {
                    for (final MapEntry(:key, :value) in _themeLabels.entries)
                      key: (value, null),
                  },
                );
                if (mode != null) {
                  await controller.update((s) => s.copyWith(themeMode: mode));
                }
              },
            ),
            const _SectionHeader('图片'),
            ListTile(
              leading: const Icon(Icons.image_outlined),
              title: const Text('图片质量'),
              subtitle: Text(settings.imageQuality.label),
              onTap: () async {
                final quality = await _choose(
                  context,
                  title: '图片质量',
                  selected: settings.imageQuality,
                  options: {
                    for (final q in ImageQuality.values)
                      q: (q.label, q.description),
                  },
                );
                if (quality != null) {
                  await controller.update(
                    (s) => s.copyWith(imageQuality: quality),
                  );
                }
              },
            ),
            ListTile(
              leading: const Icon(Icons.storage_outlined),
              title: const Text('图片缓存上限'),
              subtitle: Text(
                '${formatBytes(settings.imageCacheLimitMb * _mb)}，'
                '超出后先删最早下载的图片',
              ),
              onTap: () async {
                final mb = await _choose(
                  context,
                  title: '图片缓存上限',
                  selected: settings.imageCacheLimitMb,
                  options: {
                    for (final mb in AppSettings.imageCacheLimitChoices)
                      mb: (formatBytes(mb * _mb), null),
                  },
                );
                if (mb != null) await _setCacheLimit(ref, mb);
              },
            ),
            ListTile(
              leading: const Icon(Icons.cleaning_services_outlined),
              title: const Text('清除图片缓存'),
              subtitle: Text('已用 $cacheSize'),
              onTap: () => _clearCache(context, ref),
            ),
            const _SectionHeader('其他'),
            ListTile(
              leading: const Icon(Icons.info_outline),
              title: const Text('关于 maxbo'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/about'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Lets the user pick one of [options], each a label and an optional
/// description; null if dismissed.
Future<T?> _choose<T>(
  BuildContext context, {
  required String title,
  required T selected,
  required Map<T, (String, String?)> options,
}) => showDialog<T>(
  context: context,
  builder: (context) => SimpleDialog(
    title: Text(title),
    children: [
      RadioGroup<T>(
        groupValue: selected,
        onChanged: (value) => Navigator.pop(context, value),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final MapEntry(key: value, value: (label, description))
                in options.entries)
              RadioListTile<T>(
                value: value,
                title: Text(label),
                subtitle: description == null ? null : Text(description),
              ),
          ],
        ),
      ),
    ],
  ),
);

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
      child: Text(
        title,
        style: theme.textTheme.labelLarge?.copyWith(
          color: theme.colorScheme.primary,
        ),
      ),
    );
  }
}

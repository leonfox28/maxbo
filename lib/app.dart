import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/router.dart';
import 'core/settings/app_settings.dart';
import 'core/theme.dart';

class MaxboApp extends ConsumerWidget {
  const MaxboApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp.router(
      title: 'maxbo',
      theme: buildTheme(Brightness.light),
      darkTheme: buildTheme(Brightness.dark),
      themeMode: ref.watch(settingsProvider.select((s) => s.themeMode)),
      routerConfig: ref.watch(routerProvider),
      // The UI is Chinese only, so built-in widgets (back tooltips, the
      // licence page) speak Chinese too, whatever the system language.
      locale: const Locale('zh', 'CN'),
      supportedLocales: const [Locale('zh', 'CN'), Locale('en')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
    );
  }
}

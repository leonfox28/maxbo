import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/session/session_controller.dart';
import 'core/session/session_storage.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final storage = SecureSessionStorage();
  final restored = await storage.load();
  runApp(
    ProviderScope(
      overrides: [
        sessionStorageProvider.overrideWithValue(storage),
        restoredSessionProvider.overrideWithValue(restored),
      ],
      child: const MaxboApp(),
    ),
  );
}

import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../data/models/account.dart';

/// A signed-in session as persisted between launches.
class StoredSession {
  const StoredSession({required this.account, required this.cookies});

  final Account account;

  /// Cookies keyed by site, then by cookie name.
  final Map<String, Map<String, String>> cookies;

  factory StoredSession.fromJson(Map<String, Object?> json) => StoredSession(
    account: Account.fromJson(json['account']! as Map<String, Object?>),
    cookies: {
      for (final MapEntry(:key, :value)
          in (json['cookies']! as Map<String, Object?>).entries)
        key: (value! as Map<String, Object?>).cast<String, String>(),
    },
  );

  Map<String, Object?> toJson() => {
    'account': account.toJson(),
    'cookies': cookies,
  };
}

abstract interface class SessionStorage {
  Future<StoredSession?> load();
  Future<void> save(StoredSession session);
  Future<void> clear();
}

/// Keeps the session in the Android Keystore / iOS Keychain.
class SecureSessionStorage implements SessionStorage {
  static const _key = 'session.v1';
  static const _installedKey = 'session.installed';

  final _storage = const FlutterSecureStorage();

  @override
  Future<StoredSession?> load() async {
    final prefs = SharedPreferencesAsync();
    if (await prefs.getBool(_installedKey) != true) {
      // The iOS Keychain outlives the app: drop a session left behind by a
      // previous install instead of silently signing back in.
      await _storage.delete(key: _key);
      await prefs.setBool(_installedKey, true);
      return null;
    }
    final raw = await _storage.read(key: _key);
    if (raw == null) return null;
    try {
      return StoredSession.fromJson(jsonDecode(raw) as Map<String, Object?>);
    } catch (_) {
      // A corrupt or outdated entry just means "signed out".
      await _storage.delete(key: _key);
      return null;
    }
  }

  @override
  Future<void> save(StoredSession session) =>
      _storage.write(key: _key, value: jsonEncode(session.toJson()));

  @override
  Future<void> clear() => _storage.delete(key: _key);
}

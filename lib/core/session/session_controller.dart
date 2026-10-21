import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/api/weibo_api.dart';
import '../../data/models/account.dart';
import '../net/cookie_jar.dart';
import '../net/weibo_dio.dart';
import '../net/weibo_exception.dart';
import '../util/dev_log.dart';
import 'cookie_bridge.dart';
import 'session_renewer.dart';
import 'session_storage.dart';

/// Overridden in `main()` with the session restored from secure storage.
final restoredSessionProvider = Provider<StoredSession?>((ref) => null);

final sessionStorageProvider = Provider<SessionStorage>(
  (ref) => SecureSessionStorage(),
);

final cookieBridgeProvider = Provider<CookieBridge>(
  (ref) => const CookieBridge(),
);

final sessionRenewerProvider = Provider<SessionRenewer>(
  (ref) => WebViewSessionRenewer(ref.watch(cookieBridgeProvider)),
);

/// The cookies every API request is sent with.
final cookieJarProvider = Provider<WeiboCookieJar>((ref) {
  final jar = WeiboCookieJar(ref.watch(restoredSessionProvider)?.cookies);
  ref.onDispose(jar.dispose);
  return jar;
});

final sessionProvider = NotifierProvider<SessionController, SessionState>(
  SessionController.new,
);

sealed class SessionState {
  const SessionState();
}

final class SignedOut extends SessionState {
  const SignedOut({this.expired = false});

  /// Whether Weibo ended the session, as opposed to the user signing out.
  final bool expired;
}

final class SignedIn extends SessionState {
  const SignedIn(this.account);

  final Account account;
}

class SessionController extends Notifier<SessionState> {
  Future<void> _lastSave = Future.value();
  Future<bool>? _renewal;
  DateTime? _renewedAt;

  @override
  SessionState build() {
    final jar = ref.watch(cookieJarProvider);
    // Weibo rotates cookies (e.g. XSRF-TOKEN) on responses; keep the stored
    // copy current so the next launch starts with what the server expects.
    void persist() => unawaited(_save());
    jar.addListener(persist);
    ref.onDispose(() => jar.removeListener(persist));

    final restored = ref.read(restoredSessionProvider);
    return restored == null ? const SignedOut() : SignedIn(restored.account);
  }

  /// Signs in with cookies taken from the login WebView.
  ///
  /// Throws `SessionExpiredException` if the cookies don't belong to a
  /// signed-in user (visitors get a `SUB` cookie too), or another
  /// `WeiboException` if the check itself failed.
  Future<void> completeLogin(Map<String, Map<String, String>> cookies) async {
    final (account, verified) = await _verify(cookies);
    state = SignedIn(account);
    ref.read(cookieJarProvider).replaceAll(verified);
    await _lastSave;
  }

  Future<void> logout() async {
    state = const SignedOut();
    ref.read(cookieJarProvider).clear();
    await _lastSave;
    await ref.read(sessionStorageProvider).clear();
    // Sign the embedded browser out too, so the next login starts fresh.
    await ref.read(cookieBridgeProvider).clearAll();
  }

  /// Called when Weibo rejects the session of a request sent at [sentAt].
  ///
  /// Renews the session as a browser would (see [SessionRenewer]) and
  /// returns whether it is good again; if it can't be, signs out. Rejections
  /// that come in together share one renewal, and one for a session renewed
  /// since the request went out needs none.
  Future<bool> renewOrExpire(DateTime sentAt) {
    if (state is! SignedIn) return Future.value(false);
    if (_renewedAt case final renewedAt? when renewedAt.isAfter(sentAt)) {
      return Future.value(true);
    }
    return _renewal ??= _renew().whenComplete(() => _renewal = null);
  }

  Future<bool> _renew() async {
    final uid = (state as SignedIn).account.uid;
    final jar = ref.read(cookieJarProvider);
    var renewed = false;
    try {
      renewed = await ref
          .read(sessionRenewerProvider)
          .renew(
            lapsedSub: jar.value('weibo.com', 'SUB'),
            adopt: (cookies) async {
              try {
                final (account, verified) = await _verify(cookies);
                // The user may have signed out meanwhile.
                if (account.uid != uid || state is! SignedIn) return false;
                jar.replaceAll(verified);
                _renewedAt = DateTime.now();
                return true;
              } on WeiboException {
                return false;
              }
            },
          );
    } on Exception catch (e) {
      devLog('session renewal: $e');
    }
    devLog('session renewal: ${renewed ? 'renewed' : 'failed'}');
    if (!renewed) expire();
    return renewed;
  }

  /// Signs out after Weibo rejected the session for good.
  ///
  /// WebView cookies are kept: if the browser session is still alive there,
  /// opening the login page signs the user straight back in.
  void expire() {
    if (state is! SignedIn) return;
    state = const SignedOut(expired: true);
    ref.read(cookieJarProvider).clear();
    final storage = ref.read(sessionStorageProvider);
    _lastSave = _lastSave.then((_) => storage.clear());
  }

  /// Checks [cookies] against the API, returning the account they sign in
  /// as and the cookies with any the server set meanwhile.
  ///
  /// Throws `SessionExpiredException` if they don't belong to a signed-in
  /// user (visitors get a `SUB` cookie too).
  Future<(Account, Map<String, Map<String, String>>)> _verify(
    Map<String, Map<String, String>> cookies,
  ) async {
    final candidate = WeiboCookieJar(cookies);
    final dio = ref.read(weiboDioFactoryProvider)(candidate);
    try {
      final account = await WeiboApi(dio).fetchCurrentAccount();
      return (account, candidate.toJson());
    } finally {
      dio.close();
      candidate.dispose();
    }
  }

  // Saves are chained so an older snapshot can never overwrite a newer one.
  Future<void> _save() {
    final current = state;
    if (current is! SignedIn) return _lastSave;
    final session = StoredSession(
      account: current.account,
      cookies: ref.read(cookieJarProvider).toJson(),
    );
    final storage = ref.read(sessionStorageProvider);
    return _lastSave = _lastSave.then((_) => storage.save(session));
  }
}

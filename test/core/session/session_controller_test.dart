import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/cookie_jar.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_dio.dart';
import 'package:maxbo/core/net/weibo_exception.dart';
import 'package:maxbo/core/session/session_controller.dart';
import 'package:maxbo/core/session/session_storage.dart';
import 'package:maxbo/data/api/weibo_api.dart';
import 'package:maxbo/data/models/account.dart';

import '../../support/fakes.dart';

const _webCookies = {
  'weibo.com': {'SUB': 'user-sub', 'XSRF-TOKEN': 'token'},
};

void main() {
  late InMemorySessionStorage storage;
  late FakeCookieBridge bridge;
  late ResponseBody Function(RequestOptions) respond;

  ProviderContainer container({StoredSession? restored}) {
    final c = ProviderContainer(
      overrides: [
        sessionStorageProvider.overrideWithValue(storage),
        cookieBridgeProvider.overrideWithValue(bridge),
        restoredSessionProvider.overrideWithValue(restored),
        weiboDioFactoryProvider.overrideWithValue(
          (WeiboCookieJar jar) => createWeiboDio(
            jar: jar,
            throttle: RequestThrottle(minInterval: Duration.zero),
          )..httpClientAdapter = FakeAdapter((o) => respond(o)),
        ),
      ],
    );
    addTearDown(c.dispose);
    return c;
  }

  setUp(() {
    storage = InMemorySessionStorage();
    bridge = FakeCookieBridge();
    respond = (_) => jsonBody({
      'ok': 1,
      'data': {'uid': '42'},
    });
  });

  test('starts signed out, or signed in with a restored session', () {
    expect(container().read(sessionProvider), isA<SignedOut>());

    final restored = container(
      restored: const StoredSession(
        account: Account(uid: '42'),
        cookies: _webCookies,
      ),
    );
    expect(
      restored.read(sessionProvider),
      isA<SignedIn>().having((s) => s.account.uid, 'uid', '42'),
    );
    expect(
      restored.read(cookieJarProvider).value('weibo.com', 'SUB'),
      'user-sub',
    );
  });

  test(
    'completeLogin verifies, then adopts and persists the cookies',
    () async {
      final c = container();

      await c.read(sessionProvider.notifier).completeLogin(_webCookies);

      expect(
        c.read(sessionProvider),
        isA<SignedIn>().having((s) => s.account.uid, 'uid', '42'),
      );
      expect(c.read(cookieJarProvider).value('weibo.com', 'SUB'), 'user-sub');
      expect(storage.stored?.account.uid, '42');
      expect(storage.stored?.cookies, _webCookies);
    },
  );

  test('completeLogin rejects visitor cookies and keeps nothing', () async {
    respond = (_) =>
        jsonBody({'ok': -100, 'url': 'https://weibo.com/login.php'});
    final c = container();

    await expectLater(
      c.read(sessionProvider.notifier).completeLogin(_webCookies),
      throwsA(isA<SessionExpiredException>()),
    );

    expect(c.read(sessionProvider), isA<SignedOut>());
    expect(c.read(cookieJarProvider).isEmpty, isTrue);
    expect(storage.stored, isNull);
  });

  test('cookies rotated by the server are persisted', () async {
    final c = container(
      restored: const StoredSession(
        account: Account(uid: '42'),
        cookies: _webCookies,
      ),
    );
    c.read(sessionProvider);

    c.read(cookieJarProvider).applySetCookies(Uri.parse('https://weibo.com/'), [
      'XSRF-TOKEN=new',
    ]);
    await Future<void>.delayed(Duration.zero);

    expect(storage.stored?.cookies['weibo.com']?['XSRF-TOKEN'], 'new');
  });

  test('logout clears memory, storage and the WebView', () async {
    final c = container();
    await c.read(sessionProvider.notifier).completeLogin(_webCookies);

    await c.read(sessionProvider.notifier).logout();

    expect(
      c.read(sessionProvider),
      isA<SignedOut>().having((s) => s.expired, 'expired', isFalse),
    );
    expect(c.read(cookieJarProvider).isEmpty, isTrue);
    expect(storage.stored, isNull);
    expect(bridge.cleared, isTrue);
  });

  test('an API call rejected by Weibo expires the session', () async {
    final c = container(
      restored: const StoredSession(
        account: Account(uid: '42'),
        cookies: _webCookies,
      ),
    );
    storage.stored = const StoredSession(
      account: Account(uid: '42'),
      cookies: _webCookies,
    );
    respond = (_) =>
        jsonBody({'ok': -100, 'url': 'https://weibo.com/login.php'});

    await expectLater(
      c.read(weiboApiProvider).getJson('https://weibo.com/ajax/feed/x'),
      throwsA(isA<SessionExpiredException>()),
    );
    await Future<void>.delayed(Duration.zero);

    expect(
      c.read(sessionProvider),
      isA<SignedOut>().having((s) => s.expired, 'expired', isTrue),
    );
    expect(c.read(cookieJarProvider).isEmpty, isTrue);
    expect(storage.stored, isNull);
    // The WebView keeps its cookies so re-login can be one tap.
    expect(bridge.cleared, isFalse);
  });
}

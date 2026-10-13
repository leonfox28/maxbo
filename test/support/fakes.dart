import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:maxbo/core/net/cookie_jar.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_dio.dart';
import 'package:maxbo/core/session/cookie_bridge.dart';
import 'package:maxbo/core/session/session_storage.dart';
import 'package:maxbo/data/api/weibo_api.dart';

/// Answers every request with [handler] and records what was sent.
class FakeAdapter implements HttpClientAdapter {
  FakeAdapter(this.handler);

  /// May return a future to hold a response back, e.g. to test races.
  final FutureOr<ResponseBody> Function(RequestOptions options) handler;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

/// A [WeiboApi] on a signed-in jar whose requests [adapter] answers.
WeiboApi fakeApi(FakeAdapter adapter) => WeiboApi(
  createWeiboDio(
    jar: WeiboCookieJar({
      'weibo.com': {'SUB': 'x'},
    }),
    throttle: RequestThrottle(minInterval: Duration.zero),
  )..httpClientAdapter = adapter,
);

ResponseBody jsonBody(
  Object json, {
  int status = 200,
  Map<String, List<String>> headers = const {},
}) => ResponseBody.fromString(
  jsonEncode(json),
  status,
  headers: {
    Headers.contentTypeHeader: ['application/json; charset=utf-8'],
    ...headers,
  },
);

ResponseBody htmlBody(String html, {int status = 200}) =>
    ResponseBody.fromString(
      html,
      status,
      headers: {
        Headers.contentTypeHeader: ['text/html; charset=utf-8'],
      },
    );

/// A weibo.com home page whose embedded `$CONFIG` names [uid] (0 = visitor).
ResponseBody homePage({int uid = 42}) => htmlBody(
  '<script>try{window.\$CONFIG = {"user":${uid == 0 ? 'null' : '{"idstr":"$uid","screen_name":"测试用户"}'},"uid":$uid};}catch(e){}</script>',
);

class InMemorySessionStorage implements SessionStorage {
  InMemorySessionStorage([this.stored]);

  StoredSession? stored;
  int saves = 0;

  @override
  Future<StoredSession?> load() async => stored;

  @override
  Future<void> save(StoredSession session) async {
    saves++;
    stored = session;
  }

  @override
  Future<void> clear() async => stored = null;
}

class FakeCookieBridge implements CookieBridge {
  FakeCookieBridge([this.cookies = const {}]);

  Map<String, Map<String, String>> cookies;
  bool cleared = false;

  @override
  Future<Map<String, String>> getCookies(Uri url) async =>
      cookies[url.host == 'm.weibo.cn' ? 'weibo.cn' : 'weibo.com'] ?? {};

  @override
  Future<Map<String, Map<String, String>>> readWeiboCookies() async => cookies;

  @override
  Future<void> clearAll() async {
    cleared = true;
    cookies = {};
  }
}

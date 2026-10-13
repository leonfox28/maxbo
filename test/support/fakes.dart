import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:maxbo/core/session/cookie_bridge.dart';
import 'package:maxbo/core/session/session_storage.dart';

/// Answers every request with [handler] and records what was sent.
class FakeAdapter implements HttpClientAdapter {
  FakeAdapter(this.handler);

  final ResponseBody Function(RequestOptions options) handler;
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

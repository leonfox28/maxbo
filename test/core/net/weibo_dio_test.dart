import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/cookie_jar.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_dio.dart';

import '../../support/fakes.dart';

void main() {
  late WeiboCookieJar jar;
  late FakeAdapter adapter;
  late Dio dio;

  setUp(() {
    jar = WeiboCookieJar({
      'weibo.com': {'SUB': 'sub', 'XSRF-TOKEN': 'token'},
    });
    adapter = FakeAdapter(
      (_) => jsonBody(
        {'ok': 1},
        headers: {
          'set-cookie': ['XSRF-TOKEN=rotated; path=/'],
        },
      ),
    );
    dio = createWeiboDio(
      jar: jar,
      throttle: RequestThrottle(minInterval: Duration.zero),
    )..httpClientAdapter = adapter;
  });

  test('sends browser headers, cookies and the XSRF token', () async {
    await dio.get<Object?>('https://weibo.com/ajax/config');

    final headers = adapter.requests.single.headers;
    expect(headers['User-Agent'], kBrowserUserAgent);
    expect(headers['Referer'], 'https://weibo.com/');
    expect(headers['X-Requested-With'], 'XMLHttpRequest');
    expect(headers['Cookie'], 'SUB=sub; XSRF-TOKEN=token');
    expect(headers['X-XSRF-TOKEN'], 'token');
    expect(headers.containsKey('Origin'), isFalse);
  });

  test('keeps a caller-provided Referer and adds Origin to POSTs', () async {
    await dio.post<Object?>(
      'https://weibo.com/ajax/statuses/setLike',
      options: Options(headers: {'referer': 'https://weibo.com/detail/1'}),
    );

    final headers = adapter.requests.single.headers;
    expect(headers['Referer'], 'https://weibo.com/detail/1');
    expect(headers['Origin'], 'https://weibo.com');
  });

  test('stores cookies the server sets', () async {
    await dio.get<Object?>('https://weibo.com/ajax/config');

    expect(jar.value('weibo.com', 'XSRF-TOKEN'), 'rotated');
  });

  test('sends no Weibo cookies to other hosts', () async {
    await dio.get<Object?>('https://example.com/');

    final headers = adapter.requests.single.headers;
    expect(headers.containsKey('Cookie'), isFalse);
    expect(headers.containsKey('X-XSRF-TOKEN'), isFalse);
  });
}

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'cookie_jar.dart';
import 'request_throttle.dart';
import 'weibo_exception.dart';

/// The browser identity used by both the login WebView and API requests, so
/// Weibo sees one consistent desktop browser throughout the session.
const kBrowserUserAgent =
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36';

/// Set in a request's `extra` to send it as weibo.com's pages send a plain
/// cross-origin request, such as a picture upload: with cookies, but without
/// the XHR and XSRF headers of its own endpoints.
const kPlainCrossOrigin = 'maxbo.plainCrossOrigin';

/// Creates API clients bound to a cookie jar; overridden in tests to swap the
/// HTTP adapter.
final weiboDioFactoryProvider = Provider<Dio Function(WeiboCookieJar jar)>((
  ref,
) {
  final throttle = ref.watch(requestThrottleProvider);
  return (jar) => createWeiboDio(jar: jar, throttle: throttle);
});

/// Creates a client that calls Weibo's web endpoints the way a browser tab
/// on weibo.com would.
///
/// Every HTTP status is accepted; use `unwrapResponse` to interpret replies.
Dio createWeiboDio({
  required WeiboCookieJar jar,
  required RequestThrottle throttle,
  String userAgent = kBrowserUserAgent,
}) {
  final dio = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 20),
      validateStatus: (_) => true,
      headers: {
        'User-Agent': userAgent,
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'zh-CN,zh;q=0.9',
      },
    ),
  );
  dio.interceptors.addAll([
    _ThrottleInterceptor(throttle),
    _WeiboSessionInterceptor(jar),
  ]);
  return dio;
}

class _ThrottleInterceptor extends Interceptor {
  _ThrottleInterceptor(this._throttle);

  final RequestThrottle _throttle;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    await _throttle.acquire();
    handler.next(options);
  }

  @override
  void onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) {
    _throttle.release();
    if (isRateLimitStatus(response.statusCode)) {
      _throttle.backOff();
    } else {
      _throttle.resetBackOff();
    }
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    _throttle.release();
    handler.next(err);
  }
}

/// Adds cookies and the headers Weibo's XHR endpoints expect, and stores
/// cookies the server sets.
class _WeiboSessionInterceptor extends Interceptor {
  _WeiboSessionInterceptor(this._jar);

  final WeiboCookieJar _jar;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final uri = options.uri;
    final site = WeiboCookieJar.siteOf(uri.host);
    if (site != null) {
      final origin = site == 'weibo.cn'
          ? 'https://m.weibo.cn'
          : 'https://weibo.com';
      final headers = options.headers;
      final xhr = options.extra[kPlainCrossOrigin] != true;
      headers.putIfAbsent('Referer', () => '$origin/');
      if (xhr) headers['X-Requested-With'] = 'XMLHttpRequest';
      final cookie = _jar.headerFor(uri);
      if (cookie != null) headers['Cookie'] = cookie;
      final xsrf = _jar.value(site, 'XSRF-TOKEN');
      if (xhr && xsrf != null) headers['X-XSRF-TOKEN'] = xsrf;
      if (options.method != 'GET') headers['Origin'] = origin;
    }
    handler.next(options);
  }

  @override
  void onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) {
    final setCookies = response.headers['set-cookie'];
    if (setCookies != null) _jar.applySetCookies(response.realUri, setCookies);
    handler.next(response);
  }
}

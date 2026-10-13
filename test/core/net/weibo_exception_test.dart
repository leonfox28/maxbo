import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/weibo_exception.dart';

Response<Object?> _response(
  Object? data, {
  int status = 200,
  Uri? redirectedTo,
}) => Response(
  data: data,
  statusCode: status,
  requestOptions: RequestOptions(path: 'https://weibo.com/ajax/config'),
  redirects: [
    if (redirectedTo != null) RedirectRecord(302, 'GET', redirectedTo),
  ],
);

void main() {
  test('returns the JSON object on success', () {
    final json = {
      'ok': 1,
      'data': {'x': 1},
    };
    expect(unwrapResponse(_response(json)), same(json));
    // Some endpoints carry no "ok" field at all.
    expect(unwrapResponse(_response({'statuses': []})), {'statuses': []});
  });

  test('ok -100 means signed out, or a captcha when it points at one', () {
    expect(
      () => unwrapResponse(
        _response({'ok': -100, 'url': 'https://weibo.com/login.php'}),
      ),
      throwsA(isA<SessionExpiredException>()),
    );
    expect(
      () => unwrapResponse(
        _response({'ok': -100, 'url': 'https://m.weibo.cn/captcha?x=1'}),
      ),
      throwsA(
        isA<CaptchaRequiredException>().having(
          (e) => e.url.path,
          'url.path',
          '/captcha',
        ),
      ),
    );
  });

  test('a redirect to a login host, 401, or 403 asking to log in', () {
    final cases = [
      _response(
        '<html>',
        redirectedTo: Uri.parse('https://passport.weibo.com/sso/signin'),
      ),
      _response({'ok': 0}, status: 401),
      _response({'ok': 0, 'message': '前方有点拥堵，请登录后使用'}, status: 403),
    ];
    for (final response in cases) {
      expect(
        () => unwrapResponse(response),
        throwsA(isA<SessionExpiredException>()),
      );
    }
  });

  test('rate limit statuses', () {
    for (final status in [418, 429, 432]) {
      expect(
        () => unwrapResponse(_response('', status: status)),
        throwsA(
          isA<RateLimitedException>().having(
            (e) => e.statusCode,
            'statusCode',
            status,
          ),
        ),
      );
    }
  });

  test('API errors keep Weibo\'s message', () {
    expect(
      () => unwrapResponse(_response({'ok': 0, 'msg': '内容不存在'})),
      throwsA(
        isA<WeiboApiException>().having((e) => e.message, 'message', '内容不存在'),
      ),
    );
    expect(
      () => unwrapResponse(_response({'ok': 0}, status: 500)),
      throwsA(
        isA<WeiboApiException>().having((e) => e.statusCode, 'status', 500),
      ),
    );
    expect(
      () => unwrapResponse(_response('<html>not json</html>')),
      throwsA(isA<WeiboApiException>()),
    );
  });

  test('maps transport failures to NetworkException', () {
    final options = RequestOptions(path: '/');
    expect(
      mapDioException(
        DioException.connectionTimeout(
          timeout: Duration.zero,
          requestOptions: options,
        ),
      ),
      isA<NetworkException>(),
    );
    expect(
      mapDioException(
        DioException(
          requestOptions: options,
          error: const SocketException('down'),
        ),
      ),
      isA<NetworkException>(),
    );
  });
}

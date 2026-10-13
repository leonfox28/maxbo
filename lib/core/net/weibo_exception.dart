import 'dart:io' show SocketException;

import 'package:dio/dio.dart';

/// A failure talking to Weibo, carrying a message fit to show the user.
sealed class WeiboException implements Exception {
  const WeiboException(this.message);

  final String message;

  @override
  String toString() => '$runtimeType: $message';
}

/// The stored session is missing, expired, or was rejected.
final class SessionExpiredException extends WeiboException {
  const SessionExpiredException() : super('登录已过期，请重新登录');
}

/// Weibo wants the user to pass a captcha at [url] before continuing.
final class CaptchaRequiredException extends WeiboException {
  const CaptchaRequiredException(this.url) : super('需要先完成微博的安全验证');

  final Uri url;
}

final class RateLimitedException extends WeiboException {
  const RateLimitedException(this.statusCode) : super('请求太频繁，请稍后再试');

  final int statusCode;
}

final class NetworkException extends WeiboException {
  const NetworkException() : super('网络连接失败，请检查网络');
}

/// Weibo answered, but with an error or a response we don't understand.
final class WeiboApiException extends WeiboException {
  const WeiboApiException(super.message, {this.statusCode});

  final int? statusCode;
}

const _rateLimitStatuses = {418, 429, 432};

bool isRateLimitStatus(int? statusCode) =>
    _rateLimitStatuses.contains(statusCode);

const _loginHosts = {
  'passport.weibo.com',
  'passport.weibo.cn',
  'login.sina.com.cn',
};

bool isLoginHost(String host) => _loginHosts.contains(host);

/// Returns the JSON object of a Weibo web API [response], or throws the
/// matching [WeiboException].
///
/// The client accepts every HTTP status, so all of Weibo's ways of saying
/// "no" are interpreted here in one place.
Map<String, Object?> unwrapResponse(Response<Object?> response) {
  final status = response.statusCode ?? 0;
  final data = response.data;

  if (isLoginHost(response.realUri.host)) throw const SessionExpiredException();
  if (isRateLimitStatus(status)) throw RateLimitedException(status);
  if (status == 401) throw const SessionExpiredException();

  if (data is Map<String, Object?>) {
    final ok = data['ok'];
    final message = data['message'] ?? data['msg'];
    if (ok == -100) {
      final url = Uri.tryParse('${data['url'] ?? ''}');
      if (url != null && url.toString().contains('captcha')) {
        throw CaptchaRequiredException(url);
      }
      throw const SessionExpiredException();
    }
    // e.g. 403 {"ok":0,"message":"前方有点拥堵，请登录后使用"}
    if (status == 403 && '$message'.contains('登录')) {
      throw const SessionExpiredException();
    }
    if (status >= 400 || (ok != null && ok != 1 && ok != true)) {
      throw WeiboApiException(
        message is String && message.isNotEmpty ? message : '请求失败（$status）',
        statusCode: status,
      );
    }
    return data;
  }

  if (status >= 400) {
    throw WeiboApiException('请求失败（$status）', statusCode: status);
  }
  throw WeiboApiException('微博返回了无法识别的内容', statusCode: status);
}

WeiboException mapDioException(DioException e) {
  return switch (e.type) {
    DioExceptionType.connectionTimeout ||
    DioExceptionType.sendTimeout ||
    DioExceptionType.receiveTimeout ||
    DioExceptionType.connectionError ||
    DioExceptionType.badCertificate => const NetworkException(),
    DioExceptionType.unknown when e.error is SocketException =>
      const NetworkException(),
    _ => WeiboApiException(e.message ?? '请求失败'),
  };
}

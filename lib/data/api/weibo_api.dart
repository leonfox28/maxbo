import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_dio.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/json_shape.dart';
import '../models/account.dart';

final weiboApiProvider = Provider<WeiboApi>((ref) {
  final dio = ref.watch(weiboDioFactoryProvider)(ref.watch(cookieJarProvider));
  ref.onDispose(dio.close);
  return WeiboApi(
    dio,
    onSessionExpired: () => ref.read(sessionProvider.notifier).expire(),
  );
});

/// Weibo's web endpoints, as used by weibo.com in a browser.
class WeiboApi {
  WeiboApi(this._dio, {this.onSessionExpired});

  static const _web = 'https://weibo.com';

  final Dio _dio;

  /// Called whenever Weibo rejects the session, before the exception is
  /// rethrown to the caller.
  final void Function()? onSessionExpired;

  /// The signed-in account; throws [SessionExpiredException] if signed out.
  Future<Account> fetchCurrentAccount() async {
    final json = await getJson('$_web/ajax/config');
    final account = Account.tryFromConfig(json);
    if (account == null) {
      // The signed-in shape of /ajax/config is undocumented; log its shape
      // (never its values) so the parser can be fixed.
      debugPrint('[maxbo] unrecognized /ajax/config: ${describeShape(json)}');
      throw const WeiboApiException('无法识别当前登录的账号');
    }
    return account;
  }

  Future<Map<String, Object?>> getJson(
    String url, {
    Map<String, Object?>? query,
  }) async {
    try {
      final response = await _dio.get<Object?>(url, queryParameters: query);
      return unwrapResponse(response);
    } on DioException catch (e) {
      throw mapDioException(e);
    } on SessionExpiredException {
      onSessionExpired?.call();
      rethrow;
    }
  }
}

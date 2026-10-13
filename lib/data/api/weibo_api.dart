import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_dio.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/dev_log.dart';
import '../../core/util/json_shape.dart';
import '../models/account.dart';
import '../models/feed_page.dart';
import 'page_config.dart';

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

  /// The signed-in account; throws [SessionExpiredException] for visitors.
  ///
  /// weibo.com has no JSON endpoint for this; the web app reads the user from
  /// the `$CONFIG` object embedded in its home page, and so do we.
  Future<Account> fetchCurrentAccount() async {
    final html = await getPage('$_web/');
    final config = extractPageConfig(html);
    if (config == null) {
      devLog('weibo.com page has no \$CONFIG (${html.length} chars)');
      throw const WeiboApiException('无法识别当前登录的账号');
    }
    final account = Account.tryFromPageConfig(config);
    if (account == null) {
      devLog('no user in \$CONFIG: ${describeShape(config, maxDepth: 1)}');
      throw const SessionExpiredException();
    }
    return account;
  }

  /// A page of the "最新微博" timeline of [uid]: the newest page, or the one
  /// after [maxId] (a previous page's `FeedPage.nextCursor`).
  Future<FeedPage> fetchHomeTimeline({
    required String uid,
    String? maxId,
  }) async {
    final json = await getJson(
      '$_web/ajax/feed/friendstimeline',
      query: {
        // The built-in "最新微博" group; see PLAN §8 item 3.
        'list_id': '11000$uid',
        'refresh': 4,
        'count': 20,
        if (maxId == null) 'since_id': 0 else 'max_id': maxId,
      },
    );
    final page = FeedPage.fromJson(json);
    devLog(
      'timeline: ${page.statuses.length} statuses, '
      'more=${page.nextCursor != null}',
    );
    return page;
  }

  Future<Map<String, Object?>> getJson(
    String url, {
    Map<String, Object?>? query,
  }) => _guard(() async {
    final response = await _dio.get<Object?>(url, queryParameters: query);
    final data = response.data;
    _logResponse(response, 'ok=${data is Map ? data['ok'] : data.runtimeType}');
    return unwrapResponse(response);
  });

  /// Fetches an HTML page as a signed-in browser would.
  Future<String> getPage(String url) => _guard(() async {
    final response = await _dio.get<String>(
      url,
      options: Options(
        responseType: ResponseType.plain,
        headers: {'Accept': 'text/html,application/xhtml+xml'},
      ),
    );
    final status = response.statusCode ?? 0;
    _logResponse(response, '${response.data?.length ?? 0} chars');
    if (isLoginHost(response.realUri.host)) {
      throw const SessionExpiredException();
    }
    if (isRateLimitStatus(status)) throw RateLimitedException(status);
    if (status != 200) {
      throw WeiboApiException('请求失败（$status）', statusCode: status);
    }
    return response.data ?? '';
  });

  Future<T> _guard<T>(Future<T> Function() request) async {
    try {
      return await request();
    } on DioException catch (e) {
      throw mapDioException(e);
    } on SessionExpiredException {
      onSessionExpired?.call();
      rethrow;
    }
  }

  static void _logResponse(Response<Object?> response, String detail) {
    final uri = response.realUri;
    devLog(
      'GET ${response.requestOptions.uri.path} -> ${response.statusCode} '
      '$detail final=${uri.host}${uri.path}',
    );
  }
}

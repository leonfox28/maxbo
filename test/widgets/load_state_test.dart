import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/weibo_exception.dart';
import 'package:maxbo/widgets/load_state.dart';

import '../support/routes.dart';

void main() {
  final captchaUrl = Uri.parse('https://weibo.com/captcha?from=x');

  Future<void> pump(WidgetTester tester, Widget child) => tester.pumpWidget(
    MaterialApp.router(routerConfig: stubRouter(Scaffold(body: child))),
  );

  testWidgets('ErrorRetry retries', (tester) async {
    var retries = 0;
    await pump(
      tester,
      ErrorRetry(error: const NetworkException(), onRetry: () => retries++),
    );

    expect(find.text('网络连接失败，请检查网络'), findsOneWidget);
    await tester.tap(find.text('重试'));
    expect(retries, 1);
  });

  testWidgets('ErrorRetry lets the user take a security check, then retries', (
    tester,
  ) async {
    var retries = 0;
    await pump(
      tester,
      ErrorRetry(
        error: CaptchaRequiredException(captchaUrl),
        onRetry: () => retries++,
      ),
    );

    expect(find.text('需要先完成微博的安全验证'), findsOneWidget);
    await tester.tap(find.text('去验证'));
    await tester.pumpAndSettle();
    expect(openedWebUrl(tester), captchaUrl);
    expect(retries, 0);

    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(retries, 1);
  });

  testWidgets('LoadMoreFooter offers the security check too', (tester) async {
    var loads = 0;
    await pump(
      tester,
      LoadMoreFooter(
        loading: false,
        hasMore: true,
        error: CaptchaRequiredException(captchaUrl),
        onLoadMore: () => loads++,
      ),
    );

    await tester.tap(find.text('去验证'));
    await tester.pumpAndSettle();
    expect(openedWebUrl(tester), captchaUrl);

    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(loads, 1);
  });
}

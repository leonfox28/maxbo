import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/session/cookie_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('io.github.leonfox28.maxbo/cookies');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('passes the url and maps cookies by name', () async {
    MethodCall? received;
    messenger.setMockMethodCallHandler(channel, (call) async {
      received = call;
      return [
        {'name': 'SUB', 'value': '_2A25abc'},
        {'name': 'XSRF-TOKEN', 'value': 'tok=en'},
      ];
    });

    final cookies = await const CookieBridge().getCookies(
      Uri.parse('https://weibo.com/'),
    );

    expect(received?.method, 'getCookies');
    expect(received?.arguments, {'url': 'https://weibo.com/'});
    expect(cookies, {'SUB': '_2A25abc', 'XSRF-TOKEN': 'tok=en'});
  });

  test('last cookie wins when names repeat', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => [
        {'name': 'SUB', 'value': 'visitor'},
        {'name': 'SUB', 'value': 'user'},
      ],
    );

    final cookies = await const CookieBridge().getCookies(
      Uri.parse('https://weibo.com/'),
    );

    expect(cookies, {'SUB': 'user'});
  });

  test('returns an empty map when the platform returns null', () async {
    messenger.setMockMethodCallHandler(channel, (_) async => null);

    final cookies = await const CookieBridge().getCookies(
      Uri.parse('https://weibo.com/'),
    );

    expect(cookies, isEmpty);
  });
}

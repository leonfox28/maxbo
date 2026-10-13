import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/cookie_jar.dart';

void main() {
  final web = Uri.parse('https://weibo.com/ajax/config');
  final mobile = Uri.parse('https://m.weibo.cn/api/config');

  test('maps hosts to sites', () {
    expect(WeiboCookieJar.siteOf('weibo.com'), 'weibo.com');
    expect(WeiboCookieJar.siteOf('passport.weibo.com'), 'weibo.com');
    expect(WeiboCookieJar.siteOf('m.weibo.cn'), 'weibo.cn');
    expect(WeiboCookieJar.siteOf('notweibo.com'), isNull);
    expect(WeiboCookieJar.siteOf('wx1.sinaimg.cn'), isNull);
  });

  test('builds the Cookie header for the request site only', () {
    final jar = WeiboCookieJar({
      'weibo.com': {'SUB': 'a', 'XSRF-TOKEN': 'x'},
      'weibo.cn': {'SUB': 'b'},
    });

    expect(jar.headerFor(web), 'SUB=a; XSRF-TOKEN=x');
    expect(jar.headerFor(mobile), 'SUB=b');
    expect(jar.headerFor(Uri.parse('https://wx1.sinaimg.cn/a.jpg')), isNull);
  });

  test('applies Set-Cookie headers and notifies only on change', () {
    final jar = WeiboCookieJar({
      'weibo.com': {'XSRF-TOKEN': 'old'},
    });
    var notified = 0;
    jar.addListener(() => notified++);

    jar.applySetCookies(web, [
      'XSRF-TOKEN=new; path=/; secure',
      'SUBP=0033Wr=x; expires=Fri, 01-Jan-2100 00:00:00 GMT; domain=.weibo.com',
    ]);
    expect(jar.value('weibo.com', 'XSRF-TOKEN'), 'new');
    expect(jar.value('weibo.com', 'SUBP'), '0033Wr=x');
    expect(notified, 1);

    jar.applySetCookies(web, ['XSRF-TOKEN=new; path=/']);
    expect(notified, 1);
  });

  test('deletes cookies that expire immediately', () {
    final jar = WeiboCookieJar({
      'weibo.com': {'SUB': 'a', 'SUBP': 'b', 'ALF': 'c'},
    });

    jar.applySetCookies(web, [
      'SUB=deleted; Max-Age=0; path=/',
      'SUBP=deleted; expires=Thu, 01-Jan-1970 00:00:01 GMT',
      // Max-Age wins over Expires.
      'ALF=d; Max-Age=3600; expires=Thu, 01 Jan 1970 00:00:01 GMT',
    ]);

    expect(jar.names('weibo.com'), ['ALF']);
    expect(jar.value('weibo.com', 'ALF'), 'd');
  });

  test('ignores cookies set for another site and malformed headers', () {
    final jar = WeiboCookieJar();

    jar.applySetCookies(web, [
      'SUB=evil; domain=.weibo.cn',
      'novalue',
      '=nameless',
    ]);
    jar.applySetCookies(Uri.parse('https://example.com/'), ['SUB=x']);

    expect(jar.isEmpty, isTrue);
  });

  test('round-trips through JSON and drops unknown sites', () {
    final jar = WeiboCookieJar({
      'weibo.com': {'SUB': 'a'},
      'weibo.cn': {},
      'example.com': {'x': 'y'},
    });

    expect(jar.toJson(), {
      'weibo.com': {'SUB': 'a'},
    });
    expect(WeiboCookieJar(jar.toJson()).headerFor(web), 'SUB=a');
  });
}

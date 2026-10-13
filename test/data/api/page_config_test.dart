import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/api/page_config.dart';

void main() {
  test('extracts \$CONFIG embedded in a script', () {
    const html = '''
<html><head><script>
  var v = '2026.10.12.1' }; try{window.\$CONFIG = {"serverTime":1,"user":{"idstr":"42","screen_name":"a{b}\\"c"},"uid":42}; }catch(e){}
  window.other = {"x":1};
</script></head></html>''';

    final config = extractPageConfig(html);

    expect(config?['uid'], 42);
    expect((config?['user'] as Map?)?['screen_name'], 'a{b}"c');
  });

  test('returns null when absent, unterminated or not JSON', () {
    expect(extractPageConfig('<html></html>'), isNull);
    expect(extractPageConfig(r'window.$CONFIG = {"a":1'), isNull);
    expect(extractPageConfig(r"window.$CONFIG = {a: 'js-only'};"), isNull);
  });
}

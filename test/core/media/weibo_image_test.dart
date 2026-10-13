import 'package:extended_image/extended_image.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/media/weibo_image.dart';

void main() {
  test('cache key ignores avatar signatures', () {
    const base = 'https://tvax1.sinaimg.cn/crop.0.0.180.180.180/abc.jpg';
    expect(
      imageCacheKey('$base?KID=imgbed,tva&Expires=1760000000&ssig=aaa'),
      imageCacheKey('$base?KID=imgbed,tva&Expires=1760099999&ssig=bbb'),
    );
    expect(imageCacheKey('$base?Expires=1&ssig=a'), keyToMd5(base));
  });

  test('cache key keeps other query parameters and the path', () {
    const a = 'https://wx1.sinaimg.cn/wap360/abc.jpg';
    expect(imageCacheKey(a), keyToMd5(a));
    expect(imageCacheKey('$a?v=2'), isNot(imageCacheKey('$a?v=3')));
    expect(
      imageCacheKey('https://wx1.sinaimg.cn/orj960/abc.jpg'),
      isNot(imageCacheKey(a)),
    );
  });
}

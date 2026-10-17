import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/media/video_source.dart';
import 'package:maxbo/core/net/weibo_exception.dart';
import 'package:maxbo/data/models/status.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

void main() {
  // Expires=2000000000 is 2033-05-18T03:33:20Z.
  final expiry = DateTime.utc(2033, 5, 18, 3, 33, 20);
  final video = Status.tryFromJson(statusJson(pageInfo: videoPageInfo()))!
      .video;

  test('reads the expiry of signed URLs', () {
    expect(
      signedUrlExpiry('https://f.video.weibocdn.com/a.mp4?Expires=2000000000'),
      expiry,
    );
    expect(signedUrlExpiry('https://f.video.weibocdn.com/a.mp4'), isNull);
  });

  test('a stream URL is fresh until a few minutes before it expires', () {
    final early = expiry.subtract(const Duration(hours: 1));
    expect(freshStreamUrl(video, now: early), contains('mp4_720p'));

    final late = expiry.subtract(const Duration(minutes: 2));
    expect(freshStreamUrl(video, now: late), isNull);
    expect(freshStreamUrl(null, now: early), isNull);
    expect(
      freshStreamUrl(
        const VideoInfo(streamUrls: ['https://f.video.weibocdn.com/a.mp4']),
        now: late,
      ),
      'https://f.video.weibocdn.com/a.mp4',
    );
  });

  group('resolveVideoUrl', () {
    late FakeAdapter adapter;

    setUp(() {
      // A re-fetched status carries URLs signed for later.
      adapter = FakeAdapter(
        (_) =>
            jsonBody(statusJson(pageInfo: videoPageInfo(expires: 2100000000))),
      );
    });

    test('uses the known URL while it is fresh, without a request', () async {
      final source = await resolveVideoUrl(
        fakeApi(adapter),
        '1',
        known: video,
        now: expiry.subtract(const Duration(hours: 1)),
      );

      expect(source.fetched, isFalse);
      expect(source.url, contains('Expires=2000000000'));
      expect(adapter.requests, isEmpty);
    });

    test('re-fetches the status once the known URL has expired', () async {
      final source = await resolveVideoUrl(
        fakeApi(adapter),
        '5000000000000001',
        known: video,
        now: expiry,
      );

      expect(source.fetched, isTrue);
      expect(source.url, contains('Expires=2100000000'));
      final request = adapter.requests.single;
      expect(request.uri.path, '/ajax/statuses/show');
      expect(request.uri.queryParameters['id'], '5000000000000001');
    });

    test('re-fetches when asked to, or when nothing is known', () async {
      final api = fakeApi(adapter);
      final early = expiry.subtract(const Duration(hours: 1));

      await resolveVideoUrl(api, '1', known: video, refresh: true, now: early);
      await resolveVideoUrl(api, '1', now: early);

      expect(adapter.requests, hasLength(2));
    });

    test('picks the same video of a post mixing videos', () async {
      adapter = FakeAdapter(
        (_) => jsonBody(
          statusJson(
            extra: mixedMediaFields([
              videoPageInfo(name: 'first', expires: 2100000000),
              picJson('p1'),
              videoPageInfo(name: 'second', expires: 2100000000),
            ]),
          ),
        ),
      );

      final source = await resolveVideoUrl(fakeApi(adapter), '1', index: 1);

      expect(source.url, contains('/second/'));
    });

    test('reports a status that has no playable video', () async {
      adapter = FakeAdapter((_) => jsonBody(statusJson()));

      expect(
        resolveVideoUrl(fakeApi(adapter), '1'),
        throwsA(isA<WeiboApiException>()),
      );
    });
  });
}

import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/net/request_throttle.dart';
import 'package:maxbo/core/net/weibo_exception.dart';
import 'package:maxbo/data/models/status.dart';
import 'package:maxbo/features/compose/compose_draft.dart';
import 'package:maxbo/features/timeline/timeline_controller.dart';

import '../../support/fakes.dart';
import '../../support/statuses.dart';

void main() {
  late FakeAdapter adapter;
  late Directory dir;
  var uploads = 0;
  var failPost = false;

  ResponseBody respond(RequestOptions o) => switch (o.uri.path) {
    '/ajax/feed/friendstimeline' => jsonBody(feedJson([statusJson(id: 1)])),
    '/interface/pic_upload.php' => htmlBody(
      '{"code":"A00006","data":{"pics":{"pic_1":{"pid":"pid${++uploads}"}}}}',
    ),
    '/ajax/statuses/update' when failPost => jsonBody({
      'ok': 0,
      'msg': '发布太频繁',
    }),
    '/ajax/statuses/update' => jsonBody({
      'ok': 1,
      'data': statusJson(id: 9, text: '新微博', user: userJson(id: 7)),
    }),
    final path => throw StateError('unexpected request to $path'),
  };

  ProviderContainer container() {
    adapter = FakeAdapter(respond);
    final c = ProviderContainer(
      retry: noRetry,
      overrides: signedInOverrides(adapter),
    );
    addTearDown(c.dispose);
    return c;
  }

  String picture(String name) =>
      (File('${dir.path}/$name')..writeAsStringSync('GIF89a $name')).path;

  setUp(() {
    uploads = 0;
    failPost = false;
    dir = Directory.systemTemp.createTempSync('maxbo_compose');
    addTearDown(() => dir.deleteSync(recursive: true));
  });

  List<String> paths() => [for (final r in adapter.requests) r.uri.path];

  test('keeps at most nine pictures', () {
    final c = container();
    final draft = c.read(composeDraftProvider.notifier);

    expect(draft.addPictures([for (var i = 0; i < 7; i++) 'p$i']), 0);
    expect(draft.addPictures(['a', 'b', 'c']), 1);
    expect(c.read(composeDraftProvider).pictures.map((p) => p.path), [
      for (var i = 0; i < 7; i++) 'p$i',
      'a',
      'b',
    ]);

    draft.removePicture(0);
    expect(c.read(composeDraftProvider).pictures, hasLength(8));
  });

  test('uploads the pictures in order, posts, and shows the post atop the '
      'timeline', () async {
    final c = container();
    await c.read(timelineProvider.future);
    final draft = c.read(composeDraftProvider.notifier)
      ..text = '  新微博 '
      ..visibility = StatusVisibility.friends
      ..addPictures([picture('a'), picture('b')]);
    final uploading = <int>[];

    await draft.send(onUpload: uploading.add);

    expect(uploading, [0, 1]);
    expect(paths().skip(1), [
      '/interface/pic_upload.php',
      '/interface/pic_upload.php',
      '/ajax/statuses/update',
    ]);
    expect(adapter.bodies[1], 'GIF89a a');
    expect(adapter.bodies[2], 'GIF89a b');
    expect(Uri.splitQueryString(adapter.bodies.last), {
      'content': '新微博',
      'pic_id': '[{"type":"pic","pid":"pid1"},{"type":"pic","pid":"pid2"}]',
      'visible': '6',
    });
    expect(c.read(composeDraftProvider).text, isEmpty);
    expect(c.read(composeDraftProvider).pictures, isEmpty);
    expect(c.read(timelineProvider).requireValue.statuses.map((s) => s.id), [
      '9',
      '1',
    ]);
  });

  test(
    'sending again after a failure does not upload pictures again',
    () async {
      final c = container();
      final draft = c.read(composeDraftProvider.notifier)
        ..text = '新微博'
        ..addPictures([picture('a')]);
      failPost = true;

      await expectLater(draft.send(), throwsA(isA<WeiboApiException>()));
      expect(c.read(composeDraftProvider).text, '新微博');
      expect(c.read(composeDraftProvider).pictures.single.pid, 'pid1');

      failPost = false;
      await draft.send();

      expect(uploads, 1);
      expect(paths().where((p) => p == '/ajax/statuses/update'), hasLength(2));
      expect(
        Uri.splitQueryString(adapter.bodies.last)['pic_id'],
        contains('pid1'),
      );
    },
  );

  test('the timeline is left alone when it is not open', () async {
    final c = container();
    c.read(composeDraftProvider.notifier).text = '新微博';

    await c.read(composeDraftProvider.notifier).send();

    expect(paths(), ['/ajax/statuses/update']);
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/core/session/session_renewer.dart';

Map<String, Map<String, String>> _sub(String sub) => {
  'weibo.com': {'SUB': sub},
};

void main() {
  late List<String?> offered;
  late Set<String> accepted;

  RenewalTracker tracker({String? startSub = 'lapsed'}) => RenewalTracker(
    lapsedSub: 'lapsed',
    startSub: startSub,
    adopt: (cookies) async {
      final sub = cookies['weibo.com']?['SUB'];
      offered.add(sub);
      return accepted.contains(sub);
    },
  );

  setUp(() {
    offered = [];
    accepted = {'renewed'};
  });

  test('waits through pages with the lapsed session or none', () async {
    final renewal = tracker(startSub: null);

    expect(await renewal.pageLoaded({}), isNull);
    expect(await renewal.pageLoaded(_sub('lapsed')), isNull);
    expect(await renewal.pageLoaded({}), isNull);
    expect(offered, isEmpty);
  });

  test('takes the session Weibo renewed', () async {
    final renewal = tracker();

    expect(await renewal.pageLoaded(_sub('lapsed')), isNull);
    expect(await renewal.pageLoaded(_sub('renewed')), isTrue);
    expect(offered, ['renewed']);
  });

  test('takes a newer session the WebView already has', () async {
    expect(
      await tracker(startSub: 'renewed').pageLoaded(_sub('renewed')),
      isTrue,
    );
  });

  test('gives up on a session Weibo set that isn\'t the user\'s', () async {
    final renewal = tracker();

    // A visitor's, when there was nothing to renew.
    expect(await renewal.pageLoaded(_sub('visitor')), isFalse);
    expect(offered, ['visitor']);
  });

  test('waits on when the WebView\'s own session has lapsed too', () async {
    final renewal = tracker(startSub: 'web-lapsed');

    expect(await renewal.pageLoaded(_sub('web-lapsed')), isNull);
    expect(await renewal.pageLoaded(_sub('web-lapsed')), isNull);
    expect(await renewal.pageLoaded(_sub('renewed')), isTrue);
    // Each session is offered once.
    expect(offered, ['web-lapsed', 'renewed']);
  });
}

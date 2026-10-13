import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/account.dart';

void main() {
  test('reads uid from data.uid, as numbers or strings', () {
    expect(
      Account.tryFromConfig({
        'ok': 1,
        'data': {'uid': 1234567890},
      })?.uid,
      '1234567890',
    );
    expect(
      Account.tryFromConfig({
        'data': {'login': true, 'uid': '42'},
      })?.uid,
      '42',
    );
  });

  test('falls back to data.user and reads the screen name', () {
    final account = Account.tryFromConfig({
      'data': {
        'user': {'idstr': '42', 'screen_name': '测试用户'},
      },
    });

    expect(account?.uid, '42');
    expect(account?.screenName, '测试用户');
  });

  test('returns null when no signed-in user is named', () {
    expect(
      Account.tryFromConfig({
        'data': {'login': false},
      }),
      isNull,
    );
    expect(
      Account.tryFromConfig({
        'data': {'uid': 0},
      }),
      isNull,
    );
    expect(
      Account.tryFromConfig({
        'data': {'uid': ''},
      }),
      isNull,
    );
    expect(Account.tryFromConfig({'ok': 1}), isNull);
  });

  test('round-trips through JSON', () {
    const account = Account(uid: '42', screenName: 'n');
    final copy = Account.fromJson(account.toJson());
    expect(copy.uid, '42');
    expect(copy.screenName, 'n');
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/account.dart';

void main() {
  test('reads the user from \$CONFIG', () {
    final account = Account.tryFromPageConfig({
      'user': {'id': 42, 'idstr': '42', 'screen_name': '测试用户'},
      'uid': 42,
    });

    expect(account?.uid, '42');
    expect(account?.screenName, '测试用户');
  });

  test('falls back to user.idstr, then user.id', () {
    expect(
      Account.tryFromPageConfig({
        'user': {'idstr': '7'},
      })?.uid,
      '7',
    );
    expect(
      Account.tryFromPageConfig({
        'user': {'id': 8},
      })?.uid,
      '8',
    );
  });

  test('returns null for visitors', () {
    expect(Account.tryFromPageConfig({'uid': 0}), isNull);
    expect(Account.tryFromPageConfig({'uid': '', 'user': null}), isNull);
    expect(Account.tryFromPageConfig({'serverTime': 1}), isNull);
  });

  test('round-trips through JSON', () {
    const account = Account(uid: '42', screenName: 'n');
    final copy = Account.fromJson(account.toJson());
    expect(copy.uid, '42');
    expect(copy.screenName, 'n');
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:maxbo/data/models/user_profile.dart';

import '../../support/statuses.dart';

void main() {
  test('reads a profile', () {
    final profile = UserProfile.tryFromJson(
      profileDataJson(user: userJson(id: 1001, name: '示例用户')),
    )!;

    expect(profile.user.id, '1001');
    expect(profile.user.screenName, '示例用户');
    expect(profile.description, '这是一段简介');
    expect(profile.location, '上海 浦东新区');
    expect(profile.gender, 'f');
    expect(profile.followersCount, 123456);
    expect(profile.friendsCount, 321);
    expect(profile.statusesCount, 4567);
    expect(profile.following, isTrue);
    expect(profile.followsMe, isFalse);
  });

  test('takes the first of several cover images', () {
    final profile = UserProfile.tryFromJson(profileDataJson())!;

    expect(
      profile.coverUrl,
      'https://ww1.sinaimg.cn/crop.0.0.640.640.640/cover1.jpg',
    );
  });

  test('keeps the verification reason only for verified users', () {
    final verified = UserProfile.tryFromJson(
      profileDataJson(
        user: {...userJson(), 'verified': true, 'verified_type': 0},
        extra: {'verified_reason': '知名博主'},
      ),
    )!;
    final unverified = UserProfile.tryFromJson(
      profileDataJson(extra: {'verified_reason': '知名博主'}),
    )!;

    expect(verified.verifiedReason, '知名博主');
    expect(unverified.verifiedReason, isNull);
  });

  test('blank fields are absent', () {
    final profile = UserProfile.tryFromJson(
      profileDataJson(
        extra: {'description': '  ', 'location': '', 'cover_image_phone': ''},
      ),
    )!;

    expect(profile.description, isNull);
    expect(profile.location, isNull);
    expect(profile.coverUrl, isNull);
  });

  test('no user, no profile', () {
    // What profile/info sends for an unknown name, minus its message.
    expect(UserProfile.tryFromJson(null), isNull);
    expect(UserProfile.tryFromJson({'user': <String, Object?>{}}), isNull);
  });
}

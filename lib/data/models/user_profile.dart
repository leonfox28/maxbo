import '../../core/util/json_read.dart';
import 'weibo_user.dart';

/// A user as their profile page shows them.
class UserProfile {
  const UserProfile({
    required this.user,
    this.description,
    this.location,
    this.gender,
    this.verifiedReason,
    this.coverUrl,
    this.followersCount,
    this.friendsCount,
    this.statusesCount,
    this.following = false,
    this.followsMe = false,
  });

  final WeiboUser user;
  final String? description;
  final String? location;

  /// `m` or `f`.
  final String? gender;

  /// What Weibo verified the user as, e.g. a job title.
  final String? verifiedReason;
  final String? coverUrl;
  final int? followersCount;
  final int? friendsCount;
  final int? statusesCount;

  /// Whether the signed-in user follows this user.
  final bool following;

  /// Whether this user follows the signed-in user.
  final bool followsMe;

  /// Reads the `data` object of `profile/info`.
  static UserProfile? tryFromJson(Object? json) {
    final map = readMap(readMap(json)?['user']);
    final user = WeiboUser.tryFromJson(map);
    if (map == null || user == null) return null;
    return UserProfile(
      user: user,
      description: _trimmed(map['description']),
      location: _trimmed(map['location']),
      gender: readString(map['gender']),
      verifiedReason: user.verifiedType == null
          ? null
          : _trimmed(map['verified_reason']),
      // Sometimes several URLs joined with `;`.
      coverUrl: readUrl(readString(map['cover_image_phone'])?.split(';').first),
      followersCount: readInt(map['followers_count']),
      friendsCount: readInt(map['friends_count']),
      statusesCount: readInt(map['statuses_count']),
      following: map['following'] == true,
      followsMe: map['follow_me'] == true,
    );
  }

  static String? _trimmed(Object? value) {
    final text = readString(value)?.trim();
    return text == null || text.isEmpty ? null : text;
  }
}

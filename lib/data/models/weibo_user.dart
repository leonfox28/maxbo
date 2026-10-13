import '../../core/util/json_read.dart';

/// A Weibo user as embedded in statuses.
class WeiboUser {
  const WeiboUser({
    required this.id,
    required this.screenName,
    this.avatarUrl,
    this.verifiedType,
  });

  final String id;
  final String screenName;

  /// A signed URL that expires after a few hours; see `imageCacheKey`.
  final String? avatarUrl;

  /// Weibo's verification kind when verified: 0 is a person, 1–7 are
  /// organisations; null when unverified.
  final int? verifiedType;

  static WeiboUser? tryFromJson(Object? json) {
    final map = readMap(json);
    if (map == null) return null;
    final id = readString(map['idstr']) ?? readString(map['id']);
    final name = readString(map['screen_name']);
    if (id == null || name == null) return null;
    return WeiboUser(
      id: id,
      screenName: name,
      avatarUrl:
          readUrl(map['avatar_large']) ??
          readUrl(map['profile_image_url']) ??
          readUrl(map['avatar_hd']),
      verifiedType: map['verified'] == true
          ? readInt(map['verified_type'])
          : null,
    );
  }
}

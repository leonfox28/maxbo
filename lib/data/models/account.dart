import '../../core/util/json_read.dart';

/// The signed-in Weibo account.
class Account {
  const Account({required this.uid, this.screenName});

  final String uid;
  final String? screenName;

  /// Reads the account from the `$CONFIG` object weibo.com embeds in its
  /// pages, or returns null if it names no signed-in user (as for visitors).
  static Account? tryFromPageConfig(Map<String, Object?> config) {
    final user = config['user'];
    final uid =
        readString(config['uid']) ??
        (user is Map
            ? readString(user['idstr']) ?? readString(user['id'])
            : null);
    if (uid == null || uid == '0') return null;
    return Account(
      uid: uid,
      screenName: user is Map ? readString(user['screen_name']) : null,
    );
  }

  factory Account.fromJson(Map<String, Object?> json) => Account(
    uid: json['uid']! as String,
    screenName: json['screenName'] as String?,
  );

  Map<String, Object?> toJson() => {'uid': uid, 'screenName': screenName};
}

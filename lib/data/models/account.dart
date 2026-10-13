/// The signed-in Weibo account.
class Account {
  const Account({required this.uid, this.screenName});

  final String uid;
  final String? screenName;

  /// Reads the account from a weibo.com `/ajax/config` or m.weibo.cn
  /// `/api/config` response, or returns null if it names no signed-in user.
  static Account? tryFromConfig(Map<String, Object?> json) {
    final data = json['data'];
    if (data is! Map || data['login'] == false) return null;
    final user = data['user'];
    final uid =
        _string(data['uid']) ??
        (user is Map ? _string(user['idstr']) ?? _string(user['id']) : null);
    if (uid == null || uid == '0') return null;
    final screenName = user is Map
        ? _string(user['screen_name'])
        : _string(data['screen_name']);
    return Account(uid: uid, screenName: screenName);
  }

  factory Account.fromJson(Map<String, Object?> json) => Account(
    uid: json['uid']! as String,
    screenName: json['screenName'] as String?,
  );

  Map<String, Object?> toJson() => {'uid': uid, 'screenName': screenName};

  // Weibo mixes numbers and strings for ids; normalize to non-empty strings.
  static String? _string(Object? value) => switch (value) {
    String() when value.isNotEmpty => value,
    int() => '$value',
    _ => null,
  };
}

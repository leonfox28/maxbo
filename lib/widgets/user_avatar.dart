import 'package:flutter/material.dart';

import '../core/media/weibo_image.dart';
import '../data/models/weibo_user.dart';

/// A round avatar of [user], tappable when [onTap] is set.
class UserAvatar extends StatelessWidget {
  const UserAvatar(this.user, {super.key, this.size = 40, this.onTap});

  final WeiboUser? user;
  final double size;
  final ValueChanged<WeiboUser>? onTap;

  @override
  Widget build(BuildContext context) {
    final user = this.user;
    final url = user?.avatarUrl;
    final avatar = url == null
        ? CircleAvatar(
            radius: size / 2,
            child: Icon(Icons.person, size: size * 0.6),
          )
        : ClipOval(
            child: WeiboImage(url, width: size, height: size),
          );
    final onTap = this.onTap;
    if (user == null || onTap == null) return avatar;
    return GestureDetector(onTap: () => onTap(user), child: avatar);
  }
}

/// Weibo's verification mark: gold for people, blue for organisations.
class VerifiedIcon extends StatelessWidget {
  const VerifiedIcon(this.verifiedType, {super.key, this.size = 14});

  final int verifiedType;
  final double size;

  /// Whether [verifiedType] (`WeiboUser.verifiedType`) has a mark.
  static bool shows(int? verifiedType) =>
      verifiedType != null && verifiedType >= 0 && verifiedType <= 7;

  @override
  Widget build(BuildContext context) {
    return Icon(
      Icons.verified,
      size: size,
      color: verifiedType == 0 ? Colors.amber.shade700 : Colors.blue.shade600,
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/media/weibo_image.dart';
import '../../core/settings/app_settings.dart';
import '../../core/util/format.dart';
import '../../data/models/comment.dart';
import '../../data/models/pic_info.dart';
import '../../widgets/rich_text/status_text.dart';
import '../../widgets/status_card/status_card.dart';
import '../../widgets/user_avatar.dart';
import '../interactions/likes.dart';

/// A comment with its author, pictures, time and like button; with
/// [onThread], also a preview of its replies that opens the whole thread.
class CommentTile extends StatelessWidget {
  const CommentTile(
    this.comment, {
    super.key,
    this.authorId,
    this.actions = const StatusCardActions(),
    this.onThread,
    this.onTap,
    this.onLongPress,
  });

  final Comment comment;

  /// The status author, whose comments are labelled 博主.
  final String? authorId;
  final StatusCardActions actions;
  final VoidCallback? onThread;

  /// Tapping the comment, e.g. to reply to it.
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final user = comment.user;
    final created = comment.createdAt;
    final meta = [
      if (created != null) formatPostTime(created),
      ?comment.source,
    ].join('  ');
    final onPicture = actions.onPicture;

    final tile = Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          UserAvatar(user, size: 32, onTap: actions.onUser),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        user?.screenName ?? '微博用户',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: muted,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                    if (user != null && user.id == authorId) ...[
                      const SizedBox(width: 4),
                      const _AuthorLabel(),
                    ],
                  ],
                ),
                if (comment.text.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  StatusText(
                    comment.text,
                    urls: comment.urls,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontSize: 15,
                      height: 1.45,
                    ),
                    onLink: actions.onLink,
                  ),
                ],
                if (comment.pics.isNotEmpty) ...[
                  const SizedBox(height: 6),
                  _Pictures(
                    pics: comment.pics,
                    onTap: onPicture == null
                        ? null
                        : (i) => onPicture(comment.pics, i),
                  ),
                ],
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        meta,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: muted,
                        ),
                      ),
                    ),
                    _LikeButton(comment),
                  ],
                ),
                if (onThread != null && comment.replyCount > 0) ...[
                  const SizedBox(height: 8),
                  _ThreadPreview(
                    comment: comment,
                    actions: actions,
                    onTap: onThread!,
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
    if (onTap == null && onLongPress == null) return tile;
    return InkWell(onTap: onTap, onLongPress: onLongPress, child: tile);
  }
}

class _LikeButton extends StatelessWidget {
  const _LikeButton(this.comment);

  final Comment comment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    return LikeBuilder(
      target: (kind: LikeKind.comment, id: comment.id),
      loaded: LikeState(liked: comment.liked, count: comment.likesCount),
      builder: (context, like, onToggle) {
        final color = like.liked ? theme.colorScheme.primary : muted;
        return InkWell(
          // While a like is on its way, taps do nothing (rather than reply).
          onTap: onToggle ?? () {},
          borderRadius: BorderRadius.circular(4),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  like.liked ? Icons.thumb_up : Icons.thumb_up_outlined,
                  size: 14,
                  color: color,
                ),
                const SizedBox(width: 3),
                Text(
                  like.count > 0 ? formatCount(like.count) : '赞',
                  style: theme.textTheme.bodySmall?.copyWith(color: color),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _AuthorLabel extends StatelessWidget {
  const _AuthorLabel();

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.primaryContainer,
        borderRadius: BorderRadius.circular(3),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
        child: Text(
          '博主',
          style: TextStyle(fontSize: 10, color: colors.onPrimaryContainer),
        ),
      ),
    );
  }
}

class _Pictures extends ConsumerWidget {
  const _Pictures({required this.pics, this.onTap});

  final List<PicInfo> pics;
  final ValueChanged<int>? onTap;

  static const _size = 96.0;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pixels = ref
        .watch(settingsProvider.select((s) => s.imageQuality))
        .listPixels(_size, MediaQuery.devicePixelRatioOf(context));
    return Wrap(
      spacing: 4,
      runSpacing: 4,
      children: [
        for (final (i, pic) in pics.indexed)
          GestureDetector(
            onTap: onTap == null ? null : () => onTap!(i),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: WeiboImage(
                pic.variantFor(pixels).url,
                width: _size,
                height: _size,
              ),
            ),
          ),
      ],
    );
  }
}

/// The first replies of a thread and its size; tapping opens the thread.
class _ThreadPreview extends StatelessWidget {
  const _ThreadPreview({
    required this.comment,
    required this.actions,
    required this.onTap,
  });

  final Comment comment;
  final StatusCardActions actions;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = theme.textTheme.bodyMedium?.copyWith(height: 1.4);
    return Material(
      color: theme.colorScheme.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(6),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: SizedBox(
            width: double.infinity,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final reply in comment.replies)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: StatusText(
                      reply.text,
                      urls: reply.urls,
                      author: reply.user?.screenName,
                      style: style,
                      onLink: actions.onLink,
                    ),
                  ),
                Text(
                  '共${comment.replyCount}条回复 >',
                  style: style?.copyWith(color: theme.colorScheme.primary),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';

import '../../core/media/weibo_image.dart';
import '../../core/util/format.dart';
import '../../data/models/pic_info.dart';
import '../../data/models/status.dart';
import '../../data/models/weibo_user.dart';
import '../pic_grid/pic_grid.dart';
import '../rich_text/status_text.dart';
import '../rich_text/status_text_parser.dart';
import '../user_avatar.dart';

/// What tapping parts of a status (or comment) leads to. A null callback
/// leaves its target inert.
class StatusCardActions {
  const StatusCardActions({
    this.onOpen,
    this.onLink,
    this.onUser,
    this.onExpand,
    this.onPicture,
    this.onVideo,
  });

  /// The card itself, or the retweeted post inside it.
  final ValueChanged<Status>? onOpen;
  final ValueChanged<StatusLink>? onLink;
  final ValueChanged<WeiboUser>? onUser;

  /// "展开" on a truncated long post.
  final ValueChanged<Status>? onExpand;
  final void Function(List<PicInfo> pics, int index)? onPicture;
  final ValueChanged<Status>? onVideo;
}

/// A status as shown in a timeline, or atop its detail page when [detail].
class StatusCard extends StatelessWidget {
  const StatusCard(
    this.status, {
    super.key,
    this.actions = const StatusCardActions(),
    this.detail = false,
  });

  final Status status;
  final StatusCardActions actions;

  /// On the detail page the card itself isn't tappable and the counts move
  /// to the page's tabs.
  final bool detail;

  @override
  Widget build(BuildContext context) {
    final retweeted = status.retweeted;
    final onOpen = actions.onOpen;
    final card = Padding(
      padding: EdgeInsets.fromLTRB(16, 12, 16, detail ? 12 : 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _Header(status: status, onUser: actions.onUser),
          const SizedBox(height: 8),
          _Body(status: status, actions: actions),
          if (retweeted != null) ...[
            const SizedBox(height: 8),
            _Retweeted(status: retweeted, actions: actions),
          ],
          if (!detail) _Counts(status: status),
        ],
      ),
    );
    if (detail || onOpen == null) return card;
    return InkWell(onTap: () => onOpen(status), child: card);
  }
}

class _Retweeted extends StatelessWidget {
  const _Retweeted({required this.status, required this.actions});

  final Status status;
  final StatusCardActions actions;

  @override
  Widget build(BuildContext context) {
    final onOpen = actions.onOpen;
    // Deleted or hidden posts come without an author and can't be opened.
    final canOpen = onOpen != null && status.user != null;
    return Material(
      color: Theme.of(context).colorScheme.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: canOpen ? () => onOpen(status) : null,
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: SizedBox(
            width: double.infinity,
            child: _Body(status: status, actions: actions, nested: true),
          ),
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.status, this.onUser});

  final Status status;
  final ValueChanged<WeiboUser>? onUser;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final user = status.user;
    final created = status.createdAt;
    final meta = [
      if (created != null) formatPostTime(created),
      if (status.source case final source?) '来自 $source',
      ?status.region,
    ].join('  ');
    final verified = user?.verifiedType;

    return Row(
      children: [
        UserAvatar(user, onTap: onUser),
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
                      style: theme.textTheme.titleSmall,
                    ),
                  ),
                  if (verified != null && verified >= 0 && verified <= 7) ...[
                    const SizedBox(width: 4),
                    Icon(
                      Icons.verified,
                      size: 14,
                      // Weibo's convention: gold for people, blue otherwise.
                      color: verified == 0
                          ? Colors.amber.shade700
                          : Colors.blue.shade600,
                    ),
                  ],
                ],
              ),
              if (meta.isNotEmpty)
                Text(
                  meta,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
            ],
          ),
        ),
        if (status.isAd) const _AdLabel(),
      ],
    );
  }
}

class _AdLabel extends StatelessWidget {
  const _AdLabel();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border.all(color: theme.colorScheme.outline),
        borderRadius: BorderRadius.circular(3),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
        child: Text(
          '广告',
          style: theme.textTheme.labelSmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ),
    );
  }
}

/// Text and media of a status, or of the post it retweets when [nested].
class _Body extends StatelessWidget {
  const _Body({
    required this.status,
    required this.actions,
    this.nested = false,
  });

  final Status status;
  final StatusCardActions actions;
  final bool nested;

  @override
  Widget build(BuildContext context) {
    // A retweet carries a copy of its source's page_info; show it only once,
    // inside the retweeted post.
    final isRetweet = status.retweeted != null;
    final video = isRetweet ? null : status.video;
    final card = isRetweet ? null : status.pageCard;
    final media = status.pics.isNotEmpty
        ? PicGrid(
            pics: status.pics,
            total: status.picCount,
            onTap: actions.onPicture == null
                ? null
                : (i) => actions.onPicture!(status.pics, i),
          )
        : video != null
        ? _VideoCover(
            video: video,
            onTap: actions.onVideo == null
                ? null
                : () => actions.onVideo!(status),
          )
        : card != null
        ? _PageCardView(card: card)
        : null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (status.text.isNotEmpty || (nested && status.user != null))
          StatusText(
            status.text,
            urls: status.urls,
            author: nested ? status.user?.screenName : null,
            style: Theme.of(context).textTheme.bodyMedium
                ?.copyWith(fontSize: 15, height: 1.45),
            onLink: actions.onLink,
            onExpand: actions.onExpand == null
                ? null
                : () => actions.onExpand!(status),
          ),
        if (media != null) ...[const SizedBox(height: 8), media],
      ],
    );
  }
}

class _VideoCover extends StatelessWidget {
  const _VideoCover({required this.video, this.onTap});

  final VideoInfo video;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final cover = video.coverUrl;
    final duration = video.duration;
    // Portrait videos are cropped square to keep the timeline compact.
    final ratio = (video.aspectRatio ?? 16 / 9).clamp(1.0, 16 / 9);
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: AspectRatio(
          aspectRatio: ratio,
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (cover != null)
                WeiboImage(cover)
              else
                ColoredBox(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                ),
              const Center(
                child: Icon(
                  Icons.play_circle_fill,
                  size: 52,
                  color: Colors.white70,
                  shadows: [Shadow(blurRadius: 8)],
                ),
              ),
              if (duration != null && duration > 0)
                Positioned(
                  right: 6,
                  bottom: 6,
                  child: MediaBadge(formatDuration(duration)),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PageCardView extends StatelessWidget {
  const _PageCardView({required this.card});

  final PageCard card;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final image = card.imageUrl;
    final description = card.description;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainer,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        children: [
          if (image != null)
            ClipRRect(
              borderRadius: const BorderRadius.horizontal(
                left: Radius.circular(6),
              ),
              child: WeiboImage(image, width: 64, height: 64),
            ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    card.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleSmall,
                  ),
                  if (description != null)
                    Text(
                      description,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Repost, comment and like counts. Display only until interactions (M5).
class _Counts extends StatelessWidget {
  const _Counts({required this.status});

  final Status status;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    Widget item(IconData icon, int count, String label, {Color? color}) {
      final c = color ?? colors.onSurfaceVariant;
      return Expanded(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 16, color: c),
              const SizedBox(width: 4),
              Text(
                count > 0 ? formatCount(count) : label,
                style: TextStyle(fontSize: 13, color: c),
              ),
            ],
          ),
        ),
      );
    }

    return Row(
      children: [
        item(Icons.repeat, status.repostsCount, '转发'),
        item(Icons.chat_bubble_outline, status.commentsCount, '评论'),
        item(
          status.liked ? Icons.thumb_up : Icons.thumb_up_outlined,
          status.likesCount,
          '赞',
          color: status.liked ? colors.primary : null,
        ),
      ],
    );
  }
}

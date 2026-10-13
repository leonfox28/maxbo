import 'package:flutter/material.dart';

import '../../core/media/weibo_image.dart';
import '../../core/util/format.dart';
import '../../data/models/status.dart';
import '../../data/models/weibo_user.dart';
import '../pic_grid/pic_grid.dart';
import '../rich_text/status_text.dart';
import '../rich_text/status_text_parser.dart';

/// What a status card can lead to. A null callback leaves its target inert.
class StatusCardActions {
  const StatusCardActions({
    this.onLink,
    this.onUser,
    this.onExpand,
    this.onPicture,
    this.onVideo,
  });

  final ValueChanged<StatusLink>? onLink;
  final ValueChanged<WeiboUser>? onUser;

  /// "展开" on a truncated long post.
  final ValueChanged<Status>? onExpand;
  final void Function(Status status, int index)? onPicture;
  final ValueChanged<Status>? onVideo;
}

/// A status as shown in a timeline.
class StatusCard extends StatelessWidget {
  const StatusCard(
    this.status, {
    super.key,
    this.actions = const StatusCardActions(),
  });

  final Status status;
  final StatusCardActions actions;

  @override
  Widget build(BuildContext context) {
    final retweeted = status.retweeted;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _Header(status: status, onUser: actions.onUser),
          const SizedBox(height: 8),
          _Body(status: status, actions: actions),
          if (retweeted != null) ...[
            const SizedBox(height: 8),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.surfaceContainerHigh,
                borderRadius: BorderRadius.circular(8),
              ),
              child: _Body(status: retweeted, actions: actions, nested: true),
            ),
          ],
          _Counts(status: status),
        ],
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
        GestureDetector(
          onTap: user == null || onUser == null ? null : () => onUser!(user),
          child: _Avatar(url: user?.avatarUrl),
        ),
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

class _Avatar extends StatelessWidget {
  const _Avatar({this.url});

  final String? url;

  static const _size = 40.0;

  @override
  Widget build(BuildContext context) {
    final url = this.url;
    if (url == null) {
      return const CircleAvatar(radius: _size / 2, child: Icon(Icons.person));
    }
    return ClipOval(
      child: WeiboImage(url, width: _size, height: _size),
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
                : (i) => actions.onPicture!(status, i),
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

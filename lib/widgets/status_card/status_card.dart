import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/media/weibo_image.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/format.dart';
import '../../data/models/pic_info.dart';
import '../../data/models/status.dart';
import '../../data/models/weibo_user.dart';
import '../../features/interactions/likes.dart';
import '../adaptive_layout.dart';
import '../pic_grid/pic_grid.dart';
import '../rich_text/status_text.dart';
import '../rich_text/status_text_parser.dart';
import '../tag_label.dart';
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
    this.onRepost,
    this.onDelete,
  });

  /// The card itself, or the retweeted post inside it.
  final ValueChanged<Status>? onOpen;
  final ValueChanged<StatusLink>? onLink;
  final ValueChanged<WeiboUser>? onUser;

  /// "展开" on a truncated long post.
  final ValueChanged<Status>? onExpand;
  final void Function(List<PicInfo> pics, int index)? onPicture;

  /// A video; [index] picks one in a post mixing pictures and videos, as
  /// `Status.videoAt` does.
  final void Function(Status status, int? index)? onVideo;

  /// The repost count, which reposts the card's post.
  final ValueChanged<Status>? onRepost;

  /// Deleting a post of the signed-in user's, offered on their posts only.
  final ValueChanged<Status>? onDelete;

  StatusCardActions copyWith({
    ValueChanged<StatusLink>? onLink,
    ValueChanged<WeiboUser>? onUser,
    ValueChanged<Status>? onExpand,
    ValueChanged<Status>? onDelete,
  }) => StatusCardActions(
    onOpen: onOpen,
    onLink: onLink ?? this.onLink,
    onUser: onUser ?? this.onUser,
    onExpand: onExpand ?? this.onExpand,
    onPicture: onPicture,
    onVideo: onVideo,
    onRepost: onRepost,
    onDelete: onDelete ?? this.onDelete,
  );
}

/// A status as shown in a timeline, or atop its detail page when [detail].
///
/// Given [wideCardFrom] or more across, as a [feedWidth] timeline gives it,
/// a timeline card puts its pictures, video or retweeted post beside its
/// text rather than under it: the text keeps to about the length of line it
/// has on a phone, the media takes the rest, and the card takes less
/// height. The counts stay under the text, at the foot of the card when the
/// media runs further down. Text-only cards keep their text to the left, so
/// all the text of a timeline lines up, in lines no longer than a reading
/// column's.
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
    final onOpen = actions.onOpen;
    final content = detail
        ? _narrow()
        : LayoutBuilder(
            builder: (context, constraints) =>
                constraints.maxWidth + 32 >= wideCardFrom ? _wide() : _narrow(),
          );
    final activity = detail ? null : status.activity;
    final card = Padding(
      padding: EdgeInsets.fromLTRB(16, 12, 16, detail ? 12 : 0),
      child: activity == null
          ? content
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _Activity(activity),
                const SizedBox(height: 8),
                content,
              ],
            ),
    );
    if (detail || onOpen == null) return card;
    return InkWell(onTap: () => onOpen(status), child: card);
  }

  Widget _narrow() {
    final retweeted = status.retweeted;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Header(status: status, actions: actions),
        const SizedBox(height: 8),
        _Body(status: status, actions: actions),
        if (retweeted != null) ...[
          const SizedBox(height: 8),
          _Retweeted(status: retweeted, actions: actions),
        ],
        if (!detail) _Counts(status: status, actions: actions),
      ],
    );
  }

  Widget _wide() {
    final retweeted = status.retweeted;
    // A retweet's own pictures, if it has any, stay with its text.
    final aside = retweeted != null
        ? _Retweeted(status: retweeted, actions: actions)
        : _mediaOf(status, actions);
    return _WideLayout(
      main: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _Header(status: status, actions: actions),
          const SizedBox(height: 8),
          _Body(status: status, actions: actions, withMedia: retweeted != null),
        ],
      ),
      footer: _Counts(status: status, actions: actions),
      aside: aside,
    );
  }
}

enum _WideSlot { main, footer, aside }

/// A wide card's text ([main]) with [footer] under it, and [aside], if any,
/// to their right. The footer comes right after the text, or at the foot of the card
/// if the aside runs further down, so it never leaves a gap under the aside.
class _WideLayout
    extends SlottedMultiChildRenderObjectWidget<_WideSlot, RenderBox> {
  const _WideLayout({
    required this.main,
    required this.footer,
    required this.aside,
  });

  final Widget main;
  final Widget footer;
  final Widget? aside;

  @override
  Iterable<_WideSlot> get slots => _WideSlot.values;

  @override
  Widget? childForSlot(_WideSlot slot) => switch (slot) {
    _WideSlot.main => main,
    _WideSlot.footer => footer,
    _WideSlot.aside => aside,
  };

  @override
  _RenderWideLayout createRenderObject(BuildContext context) =>
      _RenderWideLayout();
}

class _RenderWideLayout extends RenderBox
    with SlottedContainerRenderObjectMixin<_WideSlot, RenderBox> {
  /// Beside an aside the text keeps to about a phone's width of line, and
  /// the aside takes the rest: it is what catches the eye, and most posts
  /// are a line or two.
  static const _mainWidth = 400.0;
  static const _asideGap = 24.0;

  /// With no aside, the longest line the text runs to.
  static const _mainAloneWidth = 640.0;

  /// Under the aside, as above it at the top of the card.
  static const _asideBottom = 12.0;

  @override
  void performLayout() {
    final width = constraints.maxWidth;
    final main = childForSlot(_WideSlot.main)!;
    final footer = childForSlot(_WideSlot.footer)!;
    final aside = childForSlot(_WideSlot.aside);
    final mainWidth = math.min(
      width,
      aside == null ? _mainAloneWidth : _mainWidth,
    );
    final asideWidth = width - _asideGap - mainWidth;

    main.layout(
      BoxConstraints.tightFor(width: mainWidth),
      parentUsesSize: true,
    );
    footer.layout(
      BoxConstraints.tightFor(width: mainWidth),
      parentUsesSize: true,
    );
    aside?.layout(BoxConstraints(maxWidth: asideWidth), parentUsesSize: true);

    final height = math.max(
      main.size.height + footer.size.height,
      aside == null ? 0.0 : aside.size.height + _asideBottom,
    );
    size = constraints.constrain(Size(width, height));
    _place(main, Offset.zero);
    _place(footer, Offset(0, size.height - footer.size.height));
    if (aside != null) _place(aside, Offset(mainWidth + _asideGap, 0));
  }

  static void _place(RenderBox child, Offset offset) =>
      (child.parentData! as BoxParentData).offset = offset;

  @override
  void paint(PaintingContext context, Offset offset) {
    for (final child in children) {
      context.paintChild(
        child,
        offset + (child.parentData! as BoxParentData).offset,
      );
    }
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    for (final child in children) {
      final hit = result.addWithPaintOffset(
        offset: (child.parentData! as BoxParentData).offset,
        position: position,
        hitTest: (result, transformed) =>
            child.hitTest(result, position: transformed),
      );
      if (hit) return true;
    }
    return false;
  }
}

/// `Status.activity`, above the card, so a post by someone else on a user's
/// profile doesn't pass for theirs.
class _Activity extends StatelessWidget {
  const _Activity(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.colorScheme.onSurfaceVariant;
    final style = theme.textTheme.bodySmall?.copyWith(color: color);
    // The icons of the counts under a card.
    final icon = label.contains('赞')
        ? Icons.thumb_up_outlined
        : label.contains('评论')
        ? Icons.chat_bubble_outline
        : null;
    return Row(
      children: [
        if (icon != null) ...[
          Icon(icon, size: 14, color: color),
          const SizedBox(width: 6),
        ],
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: style,
          ),
        ),
      ],
    );
  }
}

class _Retweeted extends StatelessWidget {
  const _Retweeted({required this.status, required this.actions});

  final Status status;
  final StatusCardActions actions;

  @override
  Widget build(BuildContext context) {
    final onOpen = actions.onOpen;
    // Deleted or hidden posts come without an author or time and can't be
    // opened; those in `statuses/mentions` may lack only the author.
    final canOpen =
        onOpen != null && (status.user != null || status.createdAt != null);
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
  const _Header({required this.status, required this.actions});

  final Status status;
  final StatusCardActions actions;

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
    final onUser = actions.onUser;
    final onDelete = actions.onDelete;
    final visibility = status.visibility;

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
                    child: GestureDetector(
                      onTap: user == null || onUser == null
                          ? null
                          : () => onUser(user),
                      child: Text(
                        user?.screenName ?? '微博用户',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.titleSmall,
                      ),
                    ),
                  ),
                  if (VerifiedIcon.shows(verified)) ...[
                    const SizedBox(width: 4),
                    VerifiedIcon(verified!),
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
        for (final (i, tag) in [
          if (status.pinned) '置顶',
          if (visibility != StatusVisibility.public) visibility.label,
          if (status.isAd) '广告',
        ].indexed) ...[if (i > 0) const SizedBox(width: 4), TagLabel(tag)],
        if (onDelete != null && user != null)
          _OwnStatusMenu(authorId: user.id, onDelete: () => onDelete(status)),
      ],
    );
  }
}

/// A menu for the signed-in user's own posts; nothing on others'.
class _OwnStatusMenu extends ConsumerWidget {
  const _OwnStatusMenu({required this.authorId, required this.onDelete});

  final String authorId;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mine = ref.watch(
      sessionProvider.select((s) => s is SignedIn && s.account.uid == authorId),
    );
    if (!mine) return const SizedBox.shrink();
    return IconButton(
      onPressed: onDelete,
      tooltip: '更多',
      visualDensity: VisualDensity.compact,
      icon: const Icon(Icons.more_horiz),
    );
  }
}

/// The pictures, video or linked page of a status, if it has any.
Widget? _mediaOf(Status status, StatusCardActions actions) {
  // A retweet carries a copy of its source's page_info; show it only once,
  // inside the retweeted post.
  final isRetweet = status.retweeted != null;
  final video = isRetweet ? null : status.video;
  final card = isRetweet ? null : status.pageCard;
  return status.pics.isNotEmpty
      ? PicGrid(
          pics: status.pics,
          total: status.picCount,
          onTap: actions.onPicture == null
              ? null
              : (i) => actions.onPicture!(status.pics, i),
        )
      : status.mixedMedia.isNotEmpty
      ? _mixedGrid(status, actions)
      : video != null
      ? _VideoCover(
          video: video,
          onTap: actions.onVideo == null
              ? null
              : () => actions.onVideo!(status, null),
        )
      : card != null
      ? _PageCardView(card: card)
      : null;
}

// Pictures open in the viewer among the post's other pictures; videos play
// by their place among its videos.
Widget _mixedGrid(Status status, StatusCardActions actions) {
  final items = status.mixedMedia;
  final pics = [
    for (final item in items)
      if (item case MixedPic(:final pic)) pic,
  ];
  final onPicture = actions.onPicture;
  final onVideo = actions.onVideo;
  return PicGrid.mixed(
    items: items,
    total: status.picCount,
    onTap: (i) {
      final before = items.take(i);
      switch (items[i]) {
        case MixedPic():
          onPicture?.call(pics, before.whereType<MixedPic>().length);
        case MixedVideo():
          onVideo?.call(status, before.whereType<MixedVideo>().length);
      }
    },
  );
}

/// Text and media of a status, or of the post it retweets when [nested].
/// Without [withMedia], only the text, the media being shown elsewhere.
class _Body extends StatelessWidget {
  const _Body({
    required this.status,
    required this.actions,
    this.nested = false,
    this.withMedia = true,
  });

  final Status status;
  final StatusCardActions actions;
  final bool nested;
  final bool withMedia;

  @override
  Widget build(BuildContext context) {
    final media = withMedia ? _mediaOf(status, actions) : null;
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

/// Repost, comment and like counts; the repost count is a repost button and
/// the like count a like button.
class _Counts extends StatelessWidget {
  const _Counts({required this.status, required this.actions});

  final Status status;
  final StatusCardActions actions;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    Widget item(
      IconData icon,
      int count,
      String label, {
      Color? color,
      VoidCallback? onTap,
    }) {
      final c = color ?? colors.onSurfaceVariant;
      final content = Padding(
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
      );
      // No ink on the counts. An InkWell rather than a GestureDetector all
      // the same, so a press keeps the card's own ink from showing.
      return Expanded(
        child: onTap == null
            ? content
            : InkWell(
                onTap: onTap,
                splashFactory: NoSplash.splashFactory,
                highlightColor: Colors.transparent,
                child: content,
              ),
      );
    }

    final onRepost = actions.onRepost;
    final onOpen = actions.onOpen;
    return Row(
      children: [
        item(
          Icons.repeat,
          status.repostsCount,
          '转发',
          onTap: onRepost == null ? null : () => onRepost(status),
        ),
        // Opens the status, as the card does, without lighting it all up.
        item(
          Icons.chat_bubble_outline,
          status.commentsCount,
          '评论',
          onTap: onOpen == null ? null : () => onOpen(status),
        ),
        LikeBuilder(
          target: (kind: LikeKind.status, id: status.id),
          loaded: LikeState(liked: status.liked, count: status.likesCount),
          builder: (context, like, onToggle) => item(
            like.liked ? Icons.thumb_up : Icons.thumb_up_outlined,
            like.count,
            '赞',
            color: like.liked ? colors.primary : null,
            // While a like is on its way, the button takes no taps but
            // still swallows them, so they don't open the status.
            onTap: onToggle ?? () {},
          ),
        ),
      ],
    );
  }
}

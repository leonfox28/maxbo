import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/media/weibo_image.dart';
import '../../core/settings/app_settings.dart';
import '../../core/util/format.dart';
import '../../data/models/pic_info.dart';
import '../../data/models/status.dart';

/// The pictures of a status: one sized to its shape, otherwise a grid of
/// square cells (2×2 for four, three columns else, at most nine). Posts
/// mixing pictures and videos show both in it.
class PicGrid extends StatelessWidget {
  PicGrid({super.key, required List<PicInfo> pics, int? total, this.onTap})
    : items = [for (final pic in pics) MixedPic(pic)],
      total = total ?? pics.length;

  const PicGrid.mixed({super.key, required this.items, int? total, this.onTap})
    : total = total ?? items.length;

  final List<MixedMedia> items;

  /// How many items the status has; beyond nine shows as "+N".
  final int total;

  /// Called with the index in [items].
  final ValueChanged<int>? onTap;

  static const _gap = 4.0;
  static const _maxShown = 9;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        if (items.length == 1) return _single(context, items.first, width);

        final cell = (width - _gap * 2) / 3;
        final columns = items.length == 4 ? 2 : 3;
        final shown = items.take(_maxShown).toList();
        final hidden = total - shown.length;
        return SizedBox(
          width: cell * columns + _gap * (columns - 1),
          child: Wrap(
            spacing: _gap,
            runSpacing: _gap,
            children: [
              for (final (i, item) in shown.indexed)
                _tile(
                  item,
                  width: cell,
                  height: cell,
                  overlay: i == shown.length - 1 && hidden > 0
                      ? '+$hidden'
                      : null,
                  onTap: onTap == null ? null : () => onTap!(i),
                ),
            ],
          ),
        );
      },
    );
  }

  Widget _single(BuildContext context, MixedMedia item, double width) {
    // Extreme shapes are cropped; long pictures show their top.
    final aspectRatio = switch (item) {
      MixedPic(:final pic) => pic.aspectRatio,
      MixedVideo(:final video) => video.aspectRatio,
    };
    final ratio = (aspectRatio ?? 1).clamp(0.6, 1.8);
    final maxSide = width * 0.7;
    return _tile(
      item,
      width: ratio >= 1 ? maxSide : maxSide * ratio,
      height: ratio >= 1 ? maxSide / ratio : maxSide,
      onTap: onTap == null ? null : () => onTap!(0),
    );
  }

  static Widget _tile(
    MixedMedia item, {
    required double width,
    required double height,
    String? overlay,
    VoidCallback? onTap,
  }) => switch (item) {
    MixedPic(:final pic) => _PicTile(
      pic: pic,
      width: width,
      height: height,
      overlay: overlay,
      onTap: onTap,
    ),
    MixedVideo(:final video) => _VideoTile(
      video: video,
      width: width,
      height: height,
      overlay: overlay,
      onTap: onTap,
    ),
  };
}

class _PicTile extends ConsumerWidget {
  const _PicTile({
    required this.pic,
    required this.width,
    required this.height,
    this.overlay,
    this.onTap,
  });

  final PicInfo pic;
  final double width;
  final double height;
  final String? overlay;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final quality = ref.watch(settingsProvider.select((s) => s.imageQuality));
    final variant = pic.variantFor(
      quality.listPixels(
        width < height ? width : height,
        MediaQuery.devicePixelRatioOf(context),
      ),
    );
    final badge = switch (pic.type) {
      PicType.gif => 'GIF',
      PicType.livePhoto => '实况',
      PicType.pic when pic.isLong => '长图',
      PicType.pic => null,
    };
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: SizedBox(
          width: width,
          height: height,
          child: Stack(
            fit: StackFit.expand,
            children: [
              WeiboImage(
                variant.url,
                width: width,
                height: height,
                alignment: pic.isLong ? Alignment.topCenter : Alignment.center,
              ),
              if (badge != null)
                Positioned(right: 4, bottom: 4, child: MediaBadge(badge)),
              if (overlay case final overlay?) _MoreOverlay(overlay),
            ],
          ),
        ),
      ),
    );
  }
}

/// A video's cover in the grid, marked as a video and with its length.
class _VideoTile extends StatelessWidget {
  const _VideoTile({
    required this.video,
    required this.width,
    required this.height,
    this.overlay,
    this.onTap,
  });

  final VideoInfo video;
  final double width;
  final double height;
  final String? overlay;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final cover = video.coverUrl;
    final duration = video.duration;
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: SizedBox(
          width: width,
          height: height,
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (cover != null)
                WeiboImage(cover, width: width, height: height)
              else
                ColoredBox(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                ),
              const Center(
                child: Icon(
                  Icons.play_circle_fill,
                  size: 36,
                  color: Colors.white70,
                  shadows: [Shadow(blurRadius: 8)],
                ),
              ),
              if (duration != null && duration > 0)
                Positioned(
                  right: 4,
                  bottom: 4,
                  child: MediaBadge(formatDuration(duration)),
                ),
              if (overlay case final overlay?) _MoreOverlay(overlay),
            ],
          ),
        ),
      ),
    );
  }
}

/// The "+N" over the last cell when there are more items than shown.
class _MoreOverlay extends StatelessWidget {
  const _MoreOverlay(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: Colors.black45,
      child: Center(
        child: Text(
          label,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 22,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
    );
  }
}

/// A small label over media, such as GIF or a video duration.
class MediaBadge extends StatelessWidget {
  const MediaBadge(this.label, {super.key});

  final String label;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(3),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
        child: Text(
          label,
          style: const TextStyle(color: Colors.white, fontSize: 10),
        ),
      ),
    );
  }
}

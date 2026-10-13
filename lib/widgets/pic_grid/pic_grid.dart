import 'package:flutter/material.dart';

import '../../core/media/weibo_image.dart';
import '../../data/models/pic_info.dart';

/// The pictures of a status: one sized to its shape, otherwise a grid of
/// square cells (2×2 for four, three columns else, at most nine).
class PicGrid extends StatelessWidget {
  const PicGrid({super.key, required this.pics, int? total, this.onTap})
    : total = total ?? pics.length;

  final List<PicInfo> pics;

  /// How many pictures the status has; beyond nine shows as "+N".
  final int total;
  final ValueChanged<int>? onTap;

  static const _gap = 4.0;
  static const _maxShown = 9;

  @override
  Widget build(BuildContext context) {
    if (pics.isEmpty) return const SizedBox.shrink();
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        if (pics.length == 1) return _single(context, pics.first, width);

        final cell = (width - _gap * 2) / 3;
        final columns = pics.length == 4 ? 2 : 3;
        final shown = pics.take(_maxShown).toList();
        final hidden = total - shown.length;
        return SizedBox(
          width: cell * columns + _gap * (columns - 1),
          child: Wrap(
            spacing: _gap,
            runSpacing: _gap,
            children: [
              for (final (i, pic) in shown.indexed)
                _PicTile(
                  pic: pic,
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

  Widget _single(BuildContext context, PicInfo pic, double width) {
    // Extreme shapes are cropped; long pictures show their top.
    final ratio = (pic.aspectRatio ?? 1).clamp(0.6, 1.8);
    final maxSide = width * 0.7;
    return _PicTile(
      pic: pic,
      width: ratio >= 1 ? maxSide : maxSide * ratio,
      height: ratio >= 1 ? maxSide / ratio : maxSide,
      onTap: onTap == null ? null : () => onTap!(0),
    );
  }
}

class _PicTile extends StatelessWidget {
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
  Widget build(BuildContext context) {
    final dpr = MediaQuery.devicePixelRatioOf(context);
    final variant = pic.variantFor((width < height ? width : height) * dpr);
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
              if (overlay != null)
                ColoredBox(
                  color: Colors.black45,
                  child: Center(
                    child: Text(
                      overlay!,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 22,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                ),
            ],
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

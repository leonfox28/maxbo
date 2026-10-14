import 'dart:math';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/media/weibo_image.dart';
import '../../core/settings/app_settings.dart';
import '../../data/models/pic_info.dart';
import '../../widgets/pic_grid/pic_grid.dart';

/// What the image viewer shows: [pics], starting from [initialIndex].
class ImageViewerArgs {
  const ImageViewerArgs(this.pics, {this.initialIndex = 0});

  final List<PicInfo> pics;
  final int initialIndex;
}

/// Full-screen pictures: swipe between them, pinch or double-tap to zoom,
/// tap or slide vertically to close. Pictures taller than the screen open
/// filling its width, from the top.
class ImageViewerPage extends StatefulWidget {
  const ImageViewerPage({super.key, required this.args});

  final ImageViewerArgs args;

  @override
  State<ImageViewerPage> createState() => _ImageViewerPageState();
}

class _ImageViewerPageState extends State<ImageViewerPage> {
  late final _controller = ExtendedPageController(
    initialPage: widget.args.initialIndex,
    pageSpacing: 16,
  );
  late int _index = widget.args.initialIndex;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pics = widget.args.pics;
    final badge = switch (pics[_index].type) {
      PicType.gif => 'GIF',
      // Only the still picture is shown in v1 (PLAN §4.2).
      PicType.livePhoto => '实况',
      PicType.pic => null,
    };
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light,
      child: Material(
        type: MaterialType.transparency,
        child: ExtendedImageSlidePage(
          slideAxis: SlideAxis.vertical,
          slideType: SlideType.onlyImage,
          slidePageBackgroundHandler: (offset, size) => Colors.black.withValues(
            alpha: (1 - offset.dy.abs() / (size.height / 2)).clamp(0.0, 1.0),
          ),
          child: Stack(
            children: [
              GestureDetector(
                onTap: () => context.pop(),
                child: ExtendedImageGesturePageView.builder(
                  controller: _controller,
                  itemCount: pics.length,
                  onPageChanged: (i) => setState(() => _index = i),
                  itemBuilder: (context, i) => _ViewerImage(pics[i]),
                ),
              ),
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        if (pics.length > 1)
                          Text(
                            '${_index + 1} / ${pics.length}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 15,
                              shadows: [Shadow(blurRadius: 4)],
                            ),
                          ),
                        if (badge != null) ...[
                          const SizedBox(width: 8),
                          MediaBadge(badge),
                        ],
                      ],
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

class _ViewerImage extends ConsumerWidget {
  const _ViewerImage(this.pic);

  final PicInfo pic;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final screen = MediaQuery.sizeOf(context);
    final quality = ref.watch(settingsProvider.select((s) => s.imageQuality));
    return ExtendedImage(
      image: weiboImageProvider(quality.viewerVariant(pic).url),
      fit: BoxFit.contain,
      mode: ExtendedImageMode.gesture,
      enableSlideOutPage: true,
      handleLoadingProgress: true,
      initGestureConfigHandler: (state) {
        final image = state.extendedImageInfo?.image;
        final fill = image == null
            ? 1.0
            : _widthFillScale(image.width / image.height, screen);
        return GestureConfig(
          inPageView: true,
          minScale: 1,
          initialScale: fill,
          maxScale: max(4, fill * 2),
          initialAlignment: fill > 1
              ? InitialAlignment.topCenter
              : InitialAlignment.center,
        );
      },
      onDoubleTap: (state) {
        final initial = state.imageGestureConfig?.initialScale ?? 1;
        final current = state.gestureDetails?.totalScale ?? initial;
        state.handleDoubleTap(
          scale: current > initial * 1.01 ? initial : initial * 2.5,
          doubleTapPosition: state.pointerDownPosition,
        );
      },
      loadStateChanged: (state) => switch (state.extendedImageLoadState) {
        LoadState.completed => null,
        LoadState.loading => Center(
          child: CircularProgressIndicator(
            color: Colors.white70,
            value: switch (state.loadingProgress) {
              ImageChunkEvent(
                :final cumulativeBytesLoaded,
                expectedTotalBytes: final total?,
              )
                  when total > 0 =>
                cumulativeBytesLoaded / total,
              _ => null,
            },
          ),
        ),
        LoadState.failed => Center(
          child: TextButton.icon(
            onPressed: state.reLoadImage,
            style: TextButton.styleFrom(foregroundColor: Colors.white70),
            icon: const Icon(Icons.broken_image_outlined),
            label: const Text('加载失败，点击重试'),
          ),
        ),
      },
    );
  }

  /// The zoom at which a picture of [imageAspect] (width / height) fills the
  /// screen's width: above 1 for pictures taller than the screen's shape,
  /// which would otherwise show as a thin strip.
  static double _widthFillScale(double imageAspect, Size screen) {
    final screenAspect = screen.width / screen.height;
    return imageAspect < screenAspect ? screenAspect / imageAspect : 1;
  }
}

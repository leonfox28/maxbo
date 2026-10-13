import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:video_player/video_player.dart';

import '../../core/media/video_source.dart';
import '../../core/media/weibo_image.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/util/dev_log.dart';
import '../../core/util/format.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/status.dart';

/// Plays the video of status [id] full screen.
///
/// The stream URLs of [initial] (the status as the previous page had it)
/// expire about an hour after it loaded; past that, or if the video CDN
/// rejects one, the status is fetched again for fresh URLs.
class VideoPlayerPage extends ConsumerStatefulWidget {
  const VideoPlayerPage({super.key, required this.id, this.initial});

  final String id;
  final Status? initial;

  @override
  ConsumerState<VideoPlayerPage> createState() => _VideoPlayerPageState();
}

class _VideoPlayerPageState extends ConsumerState<VideoPlayerPage> {
  VideoPlayerController? _controller;
  String? _error;
  var _controlsVisible = true;
  Timer? _hideTimer;

  // Whether the URL in use was just fetched. A URL that came with the status
  // gets one refresh when it fails; a fresh one failing is reported.
  var _fetched = false;

  // Tells an [_open] superseded by a newer one to drop its result.
  var _generation = 0;

  @override
  void initState() {
    super.initState();
    _open();
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    _controller?.dispose();
    super.dispose();
  }

  Future<void> _open({bool refresh = false, Duration? resumeAt}) async {
    final generation = ++_generation;
    final previous = _controller;
    if (previous != null) {
      previous.removeListener(_onPlayerChanged);
      // The video view still shows it until the next frame.
      WidgetsBinding.instance.addPostFrameCallback((_) => previous.dispose());
    }
    setState(() {
      _controller = null;
      _error = null;
    });

    try {
      final source = await resolveVideoUrl(
        ref.read(weiboApiProvider),
        widget.id,
        known: widget.initial?.video,
        refresh: refresh,
      );
      if (!mounted || generation != _generation) return;
      _fetched = source.fetched;
      final controller = VideoPlayerController.networkUrl(
        Uri.parse(source.url),
        httpHeaders: videoHttpHeaders,
      );
      try {
        await controller.initialize();
        if (resumeAt != null) await controller.seekTo(resumeAt);
      } on Exception {
        await controller.dispose();
        rethrow;
      }
      if (!mounted || generation != _generation) {
        await controller.dispose();
        return;
      }
      controller.addListener(_onPlayerChanged);
      setState(() => _controller = controller);
      await controller.play();
      _scheduleHide();
    } on WeiboException catch (e) {
      if (mounted && generation == _generation) {
        setState(() => _error = e.message);
      }
    } on Exception catch (e) {
      devLog('video failed to load: ${e.runtimeType}');
      _onStreamFailed(generation, resumeAt);
    }
  }

  void _onPlayerChanged() {
    final controller = _controller;
    if (controller == null) return;
    // Offer the replay button once the video ends.
    if (controller.value.isCompleted && !_controlsVisible) {
      setState(() => _controlsVisible = true);
    }
    if (!controller.value.hasError) return;
    controller.removeListener(_onPlayerChanged);
    devLog('video failed while playing');
    _onStreamFailed(_generation, controller.value.position);
  }

  void _onStreamFailed(int generation, Duration? position) {
    if (!mounted || generation != _generation) return;
    if (_fetched) {
      setState(() => _error = '视频加载失败');
    } else {
      _open(refresh: true, resumeAt: position);
    }
  }

  void _toggleControls() {
    setState(() => _controlsVisible = !_controlsVisible);
    _scheduleHide();
  }

  /// Hides the controls a while after the last interaction, during playback.
  void _scheduleHide() {
    _hideTimer?.cancel();
    if (!_controlsVisible) return;
    _hideTimer = Timer(const Duration(seconds: 3), () {
      if (mounted && (_controller?.value.isPlaying ?? false)) {
        setState(() => _controlsVisible = false);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    final error = _error;
    final cover = widget.initial?.video?.coverUrl;
    final controlsShown = _controlsVisible || controller == null;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light,
      child: Scaffold(
        backgroundColor: Colors.black,
        body: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: _toggleControls,
          child: Stack(
            fit: StackFit.expand,
            children: [
              Center(
                child: controller != null
                    ? AspectRatio(
                        aspectRatio: controller.value.aspectRatio,
                        child: VideoPlayer(controller),
                      )
                    : cover != null
                    ? WeiboImage(cover, fit: BoxFit.contain, placeholder: false)
                    : null,
              ),
              if (controller == null)
                Center(
                  child: error == null
                      ? const CircularProgressIndicator(color: Colors.white70)
                      : _ErrorPanel(
                          message: error,
                          onRetry: () => _open(refresh: true),
                        ),
                )
              else
                _Controls(
                  controller: controller,
                  visible: _controlsVisible,
                  onInteraction: _scheduleHide,
                ),
              Positioned(
                top: 0,
                left: 0,
                child: SafeArea(
                  child: IgnorePointer(
                    ignoring: !controlsShown,
                    child: AnimatedOpacity(
                      opacity: controlsShown ? 1 : 0,
                      duration: const Duration(milliseconds: 200),
                      child: IconButton(
                        tooltip: '返回',
                        color: Colors.white,
                        icon: const BackButtonIcon(),
                        onPressed: () => context.pop(),
                      ),
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

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(message, style: const TextStyle(color: Colors.white)),
        const SizedBox(height: 12),
        OutlinedButton(
          onPressed: onRetry,
          style: OutlinedButton.styleFrom(foregroundColor: Colors.white),
          child: const Text('重试'),
        ),
      ],
    );
  }
}

/// Play/pause in the middle, position and a seek bar at the bottom.
class _Controls extends StatelessWidget {
  const _Controls({
    required this.controller,
    required this.visible,
    required this.onInteraction,
  });

  final VideoPlayerController controller;
  final bool visible;
  final VoidCallback onInteraction;

  @override
  Widget build(BuildContext context) {
    const labelStyle = TextStyle(color: Colors.white, fontSize: 12);
    return IgnorePointer(
      ignoring: !visible,
      child: AnimatedOpacity(
        opacity: visible ? 1 : 0,
        duration: const Duration(milliseconds: 200),
        child: ValueListenableBuilder<VideoPlayerValue>(
          valueListenable: controller,
          builder: (context, value, _) => Stack(
            children: [
              Center(
                child: IconButton.filled(
                  iconSize: 48,
                  style: IconButton.styleFrom(
                    backgroundColor: Colors.black45,
                    foregroundColor: Colors.white,
                  ),
                  tooltip: value.isPlaying ? '暂停' : '播放',
                  icon: Icon(
                    value.isPlaying
                        ? Icons.pause
                        : value.isCompleted
                        ? Icons.replay
                        : Icons.play_arrow,
                  ),
                  onPressed: () {
                    if (value.isPlaying) {
                      controller.pause();
                    } else {
                      if (value.isCompleted) controller.seekTo(Duration.zero);
                      controller.play();
                    }
                    onInteraction();
                  },
                ),
              ),
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: DecoratedBox(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.bottomCenter,
                      end: Alignment.topCenter,
                      colors: [Colors.black54, Colors.transparent],
                    ),
                  ),
                  child: SafeArea(
                    top: false,
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                      child: Row(
                        children: [
                          Text(
                            formatDuration(value.position.inSeconds),
                            style: labelStyle,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: VideoProgressIndicator(
                              controller,
                              allowScrubbing: true,
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              colors: const VideoProgressColors(
                                playedColor: Colors.white,
                                bufferedColor: Colors.white38,
                                backgroundColor: Colors.white12,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            formatDuration(value.duration.inSeconds),
                            style: labelStyle,
                          ),
                        ],
                      ),
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

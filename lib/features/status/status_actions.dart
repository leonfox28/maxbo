import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../data/models/pic_info.dart';
import '../../data/models/status.dart';
import '../../widgets/rich_text/status_text_parser.dart';
import '../../widgets/status_card/status_card.dart';
import '../media_viewer/image_viewer_page.dart';

/// What tapping parts of a status card (or a comment) does throughout the
/// app.
StatusCardActions statusCardActions(BuildContext context) {
  void notYet(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> openExternally(Uri url) async {
    final opened = await launchUrl(url, mode: LaunchMode.externalApplication);
    if (!opened) notYet('无法打开链接');
  }

  void openStatus(Status status) =>
      context.push('/status/${status.id}', extra: status);

  void viewPictures(List<PicInfo> pics, int index) => context.push(
    '/viewer',
    extra: ImageViewerArgs(pics, initialIndex: index),
  );

  return StatusCardActions(
    onOpen: openStatus,
    onLink: (link) => switch (link) {
      WebLink(:final url) => openExternally(url),
      MentionLink() => notYet('用户主页将在 M4 实现'),
      TopicLink() => notYet('话题搜索将在 M4 实现'),
      ImageLink(:final url) when url.host.endsWith('.sinaimg.cn') =>
        viewPictures([PicInfo.fromUrl('$url')], 0),
      ImageLink(:final url) => openExternally(url),
    },
    onUser: (_) => notYet('用户主页将在 M4 实现'),
    // The detail page has the full text of long posts.
    onExpand: openStatus,
    onPicture: viewPictures,
    onVideo: (status) => context.push('/video/${status.id}', extra: status),
  );
}

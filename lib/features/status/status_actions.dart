import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../widgets/rich_text/status_text_parser.dart';
import '../../widgets/status_card/status_card.dart';

/// What tapping parts of a status card does throughout the app.
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

  return StatusCardActions(
    onLink: (link) => switch (link) {
      WebLink(:final url) => openExternally(url),
      MentionLink() => notYet('用户主页将在 M4 实现'),
      TopicLink() => notYet('话题搜索将在 M4 实现'),
      ImageLink() => notYet('图片查看将在 M3 实现'),
    },
    onUser: (_) => notYet('用户主页将在 M4 实现'),
    onExpand: (_) => notYet('全文将在 M3 实现'),
    onPicture: (_, _) => notYet('图片查看将在 M3 实现'),
    onVideo: (_) => notYet('视频播放将在 M3 实现'),
  );
}

import '../../data/api/weibo_api.dart';
import '../../data/models/status.dart';
import '../net/weibo_dio.dart';
import '../net/weibo_exception.dart';

/// Headers for Weibo's video CDN, which like its image hosts expects a
/// weibo.com Referer.
const videoHttpHeaders = {
  'Referer': 'https://weibo.com/',
  'User-Agent': kBrowserUserAgent,
};

/// When a signed media URL stops working, from its `Expires` parameter in
/// Unix seconds; null for unsigned URLs.
DateTime? signedUrlExpiry(String url) {
  final expires = int.tryParse(
    Uri.tryParse(url)?.queryParameters['Expires'] ?? '',
  );
  return expires == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(expires * 1000, isUtc: true);
}

/// A stream URL of [video] that stays valid for at least [margin] after
/// [now], or null when they have all expired or are about to.
String? freshStreamUrl(
  VideoInfo? video, {
  DateTime? now,
  Duration margin = const Duration(minutes: 5),
}) {
  final deadline = (now ?? DateTime.now()).add(margin);
  for (final url in video?.streamUrls ?? const <String>[]) {
    final expiry = signedUrlExpiry(url);
    if (expiry == null || expiry.isAfter(deadline)) return url;
  }
  return null;
}

/// Where to play the video of status [id] from: [known]'s URL while it is
/// fresh, or else (or when [refresh] is set) one from re-fetching the
/// status. [fetched] tells which, so a rejected URL is refreshed only once.
Future<({String url, bool fetched})> resolveVideoUrl(
  WeiboApi api,
  String id, {
  VideoInfo? known,
  bool refresh = false,
  DateTime? now,
}) async {
  if (!refresh) {
    if (freshStreamUrl(known, now: now) case final url?) {
      return (url: url, fetched: false);
    }
  }
  final status = await api.fetchStatus(id);
  final url = status.video?.streamUrls.firstOrNull;
  if (url == null) throw const WeiboApiException('这条微博的视频暂时无法播放');
  return (url: url, fetched: true);
}

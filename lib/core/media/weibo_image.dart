import 'package:extended_image/extended_image.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

// Weibo's image hosts answer 403 without a weibo.com Referer; sending it for
// every image is also what a browser tab on weibo.com does.
const _imageHeaders = {'Referer': 'https://weibo.com/'};

// Signature parameters on avatar URLs that change every few hours.
const _signatureParams = {'Expires', 'ssig', 'KID'};

/// The disk cache key for [url]: signature parameters are dropped, so a
/// re-signed URL for the same picture hits the cache.
String imageCacheKey(String url) {
  final uri = Uri.tryParse(url);
  if (uri == null || !uri.hasQuery) return keyToMd5(url);
  final query = Map.of(uri.queryParameters)
    ..removeWhere((key, _) => _signatureParams.contains(key));
  final path = url.substring(0, url.indexOf('?'));
  return keyToMd5(
    query.isEmpty ? path : '$path?${Uri(queryParameters: query).query}',
  );
}

ImageProvider weiboImageProvider(String url) => ExtendedNetworkImageProvider(
  url,
  headers: _imageHeaders,
  cache: true,
  cacheKey: imageCacheKey(url),
  // The package prints failures with print(), which reaches release logs.
  printError: kDebugMode,
);

/// A Weibo-hosted image with a placeholder while loading and tap-to-retry
/// when it fails.
class WeiboImage extends StatelessWidget {
  const WeiboImage(
    this.url, {
    super.key,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
    this.alignment = Alignment.center,
    this.placeholder = true,
  });

  final String url;
  final double? width;
  final double? height;
  final BoxFit fit;
  final Alignment alignment;

  /// Whether to fill the area while loading; off for inline emoticons.
  final bool placeholder;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    // Callers pick a rendition close to the displayed size (see
    // `PicInfo.variantFor`), so images are decoded as downloaded.
    return ExtendedImage(
      image: weiboImageProvider(url),
      width: width,
      height: height,
      fit: fit,
      alignment: alignment,
      loadStateChanged: (state) => switch (state.extendedImageLoadState) {
        LoadState.completed => null,
        LoadState.loading =>
          placeholder
              ? ColoredBox(color: colors.surfaceContainerHighest)
              : const SizedBox.shrink(),
        LoadState.failed => GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: state.reLoadImage,
          child: ColoredBox(
            color: colors.surfaceContainerHighest,
            child: placeholder
                ? Icon(
                    Icons.broken_image_outlined,
                    color: colors.onSurfaceVariant,
                  )
                : null,
          ),
        ),
      },
    );
  }
}

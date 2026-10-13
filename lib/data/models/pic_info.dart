import '../../core/util/json_read.dart';

enum PicType { pic, gif, livePhoto }

/// One rendition of a picture.
class PicVariant {
  const PicVariant(this.url, this.width, this.height);

  final String url;
  final int width;
  final int height;
}

/// A picture attached to a status, in the renditions Weibo offers.
class PicInfo {
  const PicInfo({
    required this.id,
    required this.type,
    required this.variants,
    this.largest,
  });

  final String id;
  final PicType type;

  /// Renditions suitable for in-list display, smallest first.
  final List<PicVariant> variants;

  /// The full-size picture, which may be many megabytes; only for viewing.
  final PicVariant? largest;

  // Names don't reliably say how big a rendition is (`original` is often a
  // mid-size crop), so variants are chosen by their actual size instead.
  static const _listVariants = ['thumbnail', 'bmiddle', 'large', 'mw2000'];
  static const _fullVariants = ['largest', 'original'];

  static PicInfo? tryFromJson(Object? json) {
    final map = readMap(json);
    if (map == null) return null;
    final id = readString(map['pic_id']) ?? readString(map['object_id']);
    final variants = [for (final key in _listVariants) ?_variant(map[key])]
      ..sort((a, b) => a.width.compareTo(b.width));
    if (id == null || variants.isEmpty) return null;
    return PicInfo(
      id: id,
      type: switch (map['type']) {
        'gif' => PicType.gif,
        'livephoto' => PicType.livePhoto,
        _ => PicType.pic,
      },
      variants: variants,
      largest: _fullVariants
          .map((key) => _variant(map[key]))
          .nonNulls
          .fold<PicVariant?>(
            null,
            (best, v) => best == null || v.width > best.width ? v : best,
          ),
    );
  }

  /// Width over height of the picture, if known.
  double? get aspectRatio {
    final v = largest ?? variants.last;
    return v.width > 0 && v.height > 0 ? v.width / v.height : null;
  }

  /// Much taller than wide; shown cropped with a badge.
  bool get isLong => (aspectRatio ?? 1) < 1 / 3;

  /// The smallest rendition whose shorter side covers [pixels], or the
  /// largest available.
  PicVariant variantFor(double pixels) {
    for (final v in variants) {
      final shorter = v.width < v.height ? v.width : v.height;
      if (shorter >= pixels) return v;
    }
    return variants.last;
  }

  static PicVariant? _variant(Object? json) {
    final map = readMap(json);
    final url = readUrl(map?['url']);
    if (map == null || url == null) return null;
    return PicVariant(
      url,
      readInt(map['width']) ?? 0,
      readInt(map['height']) ?? 0,
    );
  }
}

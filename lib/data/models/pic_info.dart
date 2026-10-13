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

  /// A picture known only by its URL, such as one linked from a status body.
  factory PicInfo.fromUrl(String url) => PicInfo(
    id: url,
    type: Uri.tryParse(url)?.path.endsWith('.gif') ?? false
        ? PicType.gif
        : PicType.pic,
    variants: [PicVariant(url, 0, 0)],
  );

  final String id;
  final PicType type;

  /// Renditions suitable for in-list display, smallest first.
  final List<PicVariant> variants;

  /// The full-size picture, which may be many megabytes; only for viewing.
  final PicVariant? largest;

  // Names don't reliably say how big a rendition is (`original` is often a
  // mid-size crop), so variants are chosen by their actual size instead.
  static const _listVariants = ['thumbnail', 'bmiddle', 'large', 'mw2000'];
  static const _fullVariants = ['largest', 'original', 'woriginal'];

  /// [id] names the picture when the JSON itself doesn't, as in comment
  /// pictures, which are keyed by id.
  static PicInfo? tryFromJson(Object? json, {String? id}) {
    final map = readMap(json);
    if (map == null) return null;
    final picId =
        readString(map['pic_id']) ?? readString(map['object_id']) ?? id;
    final variants = [for (final key in _listVariants) ?_variant(map[key])];
    // Without sizes (comment pictures), keep the smallest-first key order.
    if (variants.every((v) => v.width > 0)) {
      variants.sort((a, b) => a.width.compareTo(b.width));
    }
    if (picId == null || variants.isEmpty) return null;
    return PicInfo(
      id: picId,
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
  /// largest available. A rendition of unknown size is taken to cover it.
  PicVariant variantFor(double pixels) {
    for (final v in variants) {
      final shorter = v.width < v.height ? v.width : v.height;
      if (shorter >= pixels || shorter == 0) return v;
    }
    return variants.last;
  }

  /// What the image viewer shows: the largest list rendition (up to about
  /// 2000 px wide, enough for a phone screen), or for a GIF the full file,
  /// as smaller renditions may not be animated.
  PicVariant get viewerVariant =>
      type == PicType.gif ? largest ?? variants.last : variants.last;

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

/// The pictures of a JSON object holding `pic_infos` keyed by id, in the
/// display order of its `pic_ids`.
List<PicInfo> picsOf(Map<String, Object?> json) {
  final infos = readMap(json['pic_infos']);
  if (infos == null) return const [];
  final order = json['pic_ids'] is List
      ? (json['pic_ids']! as List).map(readString).nonNulls
      : const <String>[];
  return [
    for (final pid in order.isEmpty ? infos.keys : order)
      ?PicInfo.tryFromJson(infos[pid], id: pid),
  ];
}

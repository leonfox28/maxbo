import 'package:html/dom.dart' as dom;
import 'package:html/parser.dart' as html;

import '../../core/util/json_read.dart';
import '../../data/models/status.dart';

/// A piece of a status body.
sealed class TextSegment {
  const TextSegment();
}

final class PlainSegment extends TextSegment {
  const PlainSegment(this.text);

  final String text;
}

final class LinkSegment extends TextSegment {
  const LinkSegment(this.text, this.link, {this.iconUrl});

  final String text;
  final StatusLink link;

  /// The small type icon Weibo puts before some links (web page, video, …).
  final String? iconUrl;
}

/// An inline emoticon image such as `[doge]`.
final class EmoticonSegment extends TextSegment {
  const EmoticonSegment(this.url, this.alt);

  final String url;
  final String alt;
}

/// The "展开" marker at the end of a truncated long post.
final class ExpandSegment extends TextSegment {
  const ExpandSegment();
}

/// Where a link in a status body leads.
sealed class StatusLink {
  const StatusLink();
}

final class MentionLink extends StatusLink {
  const MentionLink(this.screenName);

  final String screenName;
}

/// A `#话题#`, without the surrounding `#`.
final class TopicLink extends StatusLink {
  const TopicLink(this.topic);

  final String topic;
}

/// A picture linked from the text rather than attached.
final class ImageLink extends StatusLink {
  const ImageLink(this.url);

  final Uri url;
}

final class WebLink extends StatusLink {
  const WebLink(this.url);

  final Uri url;
}

/// Splits the HTML `text` of a status into segments.
///
/// Weibo's markup is loose (e.g. unquoted `href` values), which the `html`
/// parser tolerates. [urls] resolves `t.cn` short links.
List<TextSegment> parseStatusText(
  String text, {
  List<UrlEntity> urls = const [],
}) {
  final segments = <TextSegment>[];
  void addText(String s) {
    if (s.isEmpty) return;
    if (segments.lastOrNull case PlainSegment(text: final previous)) {
      segments[segments.length - 1] = PlainSegment(previous + s);
    } else {
      segments.add(PlainSegment(s));
    }
  }

  void visit(dom.Node node) {
    if (node is dom.Text) return addText(node.text);
    if (node is! dom.Element) return;
    switch (node.localName) {
      case 'br':
        addText('\n');
      case 'img':
        final url = readUrl(node.attributes['src']);
        final alt = node.attributes['alt'] ?? node.attributes['title'] ?? '';
        if (url != null) {
          segments.add(EmoticonSegment(url, alt));
        } else {
          addText(alt);
        }
      case 'span' when node.classes.contains('expand'):
        segments.add(const ExpandSegment());
      case 'a':
        final label = node.text.trim();
        final link = _linkOf(node, label, urls);
        if (link == null || label.isEmpty) {
          addText(node.text);
        } else {
          final icon = node.querySelector('img.icon-link');
          segments.add(
            LinkSegment(label, link, iconUrl: readUrl(icon?.attributes['src'])),
          );
        }
      default:
        node.nodes.forEach(visit);
    }
  }

  html.parseFragment(text).nodes.forEach(visit);
  return segments;
}

StatusLink? _linkOf(dom.Element a, String label, List<UrlEntity> urls) {
  final href = a.attributes['href']?.trim();
  if (href == null || href.isEmpty) return null;

  // Mentions: <a href=/n/name usercard="name=name">@name</a>
  if (a.attributes.containsKey('usercard') || href.startsWith('/n/')) {
    final name = label.startsWith('@') ? label.substring(1) : label;
    return name.isEmpty ? null : MentionLink(name);
  }

  final uri = Uri.tryParse(
    href.startsWith('//')
        ? 'https:$href'
        : href.startsWith('/')
        ? 'https://weibo.com$href'
        : href,
  );
  // App-only schemes such as sinaweibo:// can't be opened; keep them as text.
  if (uri == null || !(uri.isScheme('https') || uri.isScheme('http'))) {
    return null;
  }

  if (uri.host == 's.weibo.com' &&
      label.startsWith('#') &&
      label.endsWith('#')) {
    final topic = label.substring(1, label.length - 1);
    if (topic.isNotEmpty) return TopicLink(topic);
  }
  if (a.attributes.containsKey('data-pid') ||
      uri.host.endsWith('.sinaimg.cn')) {
    return ImageLink(uri);
  }
  if (uri.host == 't.cn') {
    for (final entity in urls) {
      if (_sameUrl(entity.shortUrl, uri) && entity.longUrl != null) {
        return WebLink(Uri.parse(entity.longUrl!));
      }
    }
  }
  return WebLink(uri);
}

bool _sameUrl(String short, Uri uri) {
  final parsed = Uri.tryParse(short);
  return parsed != null && parsed.host == uri.host && parsed.path == uri.path;
}

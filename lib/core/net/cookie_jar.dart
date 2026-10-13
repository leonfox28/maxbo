import 'dart:io' show HttpDate, HttpException;

import 'package:flutter/foundation.dart';

/// Cookies for Weibo's two sites, kept in memory and scoped per site.
///
/// The web API spans weibo.com (desktop web API, passport) and weibo.cn
/// (m.weibo.cn). Scoping per site instead of per exact domain and path is all
/// these endpoints need, and keeps the jar trivial to persist.
class WeiboCookieJar extends ChangeNotifier {
  WeiboCookieJar([Map<String, Map<String, String>>? cookies]) {
    if (cookies != null) _replace(cookies);
  }

  static const sites = ['weibo.com', 'weibo.cn'];

  final _sites = <String, Map<String, String>>{};

  /// The site [host] belongs to, or null if it isn't a Weibo host.
  static String? siteOf(String host) {
    for (final site in sites) {
      if (host == site || host.endsWith('.$site')) return site;
    }
    return null;
  }

  bool get isEmpty => _sites.values.every((cookies) => cookies.isEmpty);

  String? value(String site, String name) => _sites[site]?[name];

  Iterable<String> names(String site) => _sites[site]?.keys ?? const [];

  /// The `Cookie` header for a request to [uri], or null if there is none.
  String? headerFor(Uri uri) {
    final site = siteOf(uri.host);
    final cookies = site == null ? null : _sites[site];
    if (cookies == null || cookies.isEmpty) return null;
    return cookies.entries.map((e) => '${e.key}=${e.value}').join('; ');
  }

  /// Applies the `Set-Cookie` headers of a response from [uri].
  void applySetCookies(Uri uri, Iterable<String> headers) {
    final site = siteOf(uri.host);
    if (site == null) return;
    var changed = false;
    for (final header in headers) {
      final cookie = _SetCookie.tryParse(header);
      if (cookie == null) continue;
      // Like a browser, ignore cookies a response tries to set for another site.
      if (cookie.domain != null && siteOf(cookie.domain!) != site) continue;
      final cookies = _sites.putIfAbsent(site, () => {});
      if (cookie.expired) {
        changed |= cookies.remove(cookie.name) != null;
      } else if (cookies[cookie.name] != cookie.value) {
        cookies[cookie.name] = cookie.value;
        changed = true;
      }
    }
    if (changed) notifyListeners();
  }

  void replaceAll(Map<String, Map<String, String>> cookies) {
    _replace(cookies);
    notifyListeners();
  }

  void clear() {
    if (_sites.isEmpty) return;
    _sites.clear();
    notifyListeners();
  }

  Map<String, Map<String, String>> toJson() => {
    for (final entry in _sites.entries)
      if (entry.value.isNotEmpty) entry.key: Map.of(entry.value),
  };

  void _replace(Map<String, Map<String, String>> cookies) {
    _sites
      ..clear()
      ..addAll({
        for (final entry in cookies.entries)
          if (sites.contains(entry.key)) entry.key: Map.of(entry.value),
      });
  }
}

class _SetCookie {
  _SetCookie(this.name, this.value, this.domain, this.expired);

  final String name;
  final String value;
  final String? domain;
  final bool expired;

  static _SetCookie? tryParse(String header) {
    final parts = header.split(';');
    final pair = parts.first;
    final eq = pair.indexOf('=');
    if (eq <= 0) return null;

    String? domain;
    int? maxAge;
    DateTime? expires;
    for (final attribute in parts.skip(1)) {
      final i = attribute.indexOf('=');
      final key = (i < 0 ? attribute : attribute.substring(0, i)).trim();
      final value = i < 0 ? '' : attribute.substring(i + 1).trim();
      switch (key.toLowerCase()) {
        case 'domain':
          domain = value.startsWith('.') ? value.substring(1) : value;
        case 'max-age':
          maxAge = int.tryParse(value);
        case 'expires':
          expires = _parseDate(value);
      }
    }
    // Max-Age takes precedence over Expires (RFC 6265, section 5.3).
    final expired = maxAge != null
        ? maxAge <= 0
        : expires != null && !expires.isAfter(DateTime.now());
    return _SetCookie(
      pair.substring(0, eq).trim(),
      pair.substring(eq + 1).trim(),
      domain,
      expired,
    );
  }

  // Cookie dates are often written "Thu, 01-Jan-1970 00:00:01 GMT", which
  // HttpDate only accepts once the dashes become spaces.
  static DateTime? _parseDate(String value) {
    for (final candidate in [value, value.replaceAll('-', ' ')]) {
      try {
        return HttpDate.parse(candidate);
      } on HttpException catch (_) {
        continue;
      }
    }
    return null;
  }
}

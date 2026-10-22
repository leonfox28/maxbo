import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../data/models/status.dart';
import '../data/models/weibo_user.dart';
import '../features/auth/login_page.dart';
import '../features/comments/comment_replies_page.dart';
import '../features/compose/compose_page.dart';
import '../features/debug/diagnostics_page.dart';
import '../features/media_viewer/image_viewer_page.dart';
import '../features/media_viewer/video_player_page.dart';
import '../features/notifications/notifications_page.dart';
import '../features/profile/me_page.dart';
import '../features/profile/profile_page.dart';
import '../features/search/search_page.dart';
import '../features/search/search_results_page.dart';
import '../features/settings/about_page.dart';
import '../features/settings/settings_page.dart';
import '../features/settings/text_size_page.dart';
import '../features/shell/home_shell.dart';
import '../features/status/status_detail_page.dart';
import '../features/timeline/timeline_page.dart';
import '../features/webview/web_page.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final router = GoRouter(
    routes: [
      StatefulShellRoute(
        pageBuilder: (_, state, shell) => _page(state, shell),
        navigatorContainerBuilder: (_, shell, branches) =>
            HomeShell(shell: shell, branches: branches),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/', builder: (_, _) => const TimelinePage()),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/search', builder: (_, _) => const SearchPage()),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/notifications',
                builder: (_, _) => const NotificationsPage(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/me', builder: (_, _) => const MePage())],
          ),
        ],
      ),
      // Pages above the tabs. `extra` carries what the previous page already
      // has, such as the status that was tapped; some can't do without it.
      GoRoute(
        path: '/status/:id',
        pageBuilder: (_, state) => _page(
          state,
          StatusDetailPage(
            id: state.pathParameters['id']!,
            initial: state.extra is Status ? state.extra! as Status : null,
          ),
        ),
      ),
      GoRoute(
        path: '/comments/:id',
        pageBuilder: (_, state) => _page(state, switch (state.extra) {
          final CommentThread thread => CommentRepliesPage(thread: thread),
          _ => const _MissingPage(),
        }),
      ),
      GoRoute(
        path: '/video/:id',
        pageBuilder: (_, state) => _page(
          state,
          VideoPlayerPage(
            id: state.pathParameters['id']!,
            index: int.tryParse(state.uri.queryParameters['i'] ?? ''),
            initial: state.extra is Status ? state.extra! as Status : null,
          ),
        ),
      ),
      GoRoute(
        path: '/viewer',
        pageBuilder: (_, state) => CustomTransitionPage(
          // See-through, so sliding a picture away reveals the page below.
          opaque: false,
          transitionsBuilder: (_, animation, _, child) =>
              FadeTransition(opacity: animation, child: child),
          child: switch (state.extra) {
            final ImageViewerArgs args => ImageViewerPage(args: args),
            _ => const _MissingPage(),
          },
        ),
      ),
      // Profiles by uid, or by name for mentions, as weibo.com has them.
      GoRoute(
        path: '/u/:uid',
        pageBuilder: (_, state) => _page(
          state,
          ProfilePage(
            uid: state.pathParameters['uid']!,
            initial: state.extra is WeiboUser
                ? state.extra! as WeiboUser
                : null,
          ),
        ),
      ),
      GoRoute(
        path: '/n/:name',
        pageBuilder: (_, state) => _page(
          state,
          ProfilePage(screenName: state.pathParameters['name']!),
        ),
      ),
      GoRoute(
        path: '/results',
        pageBuilder: (_, state) =>
            _page(state, switch (state.uri.queryParameters['q']) {
              final query? when query.trim().isNotEmpty => SearchResultsPage(
                query: query,
              ),
              _ => const _MissingPage(),
            }),
      ),
      GoRoute(
        path: '/web',
        pageBuilder: (_, state) => _page(state, switch (Uri.tryParse(
          state.uri.queryParameters['url'] ?? '',
        )) {
          final url? when url.isScheme('https') || url.isScheme('http') =>
            WebPage(url: url),
          _ => const _MissingPage(),
        }),
      ),
      GoRoute(
        path: '/login',
        pageBuilder: (_, _) =>
            const MaterialPage(fullscreenDialog: true, child: LoginPage()),
      ),
      GoRoute(
        path: '/compose',
        pageBuilder: (_, _) =>
            const MaterialPage(fullscreenDialog: true, child: ComposePage()),
      ),
      GoRoute(
        path: '/settings',
        pageBuilder: (_, state) => _page(state, const SettingsPage()),
      ),
      GoRoute(
        path: '/text-size',
        pageBuilder: (_, state) => _page(state, const TextSizePage()),
      ),
      GoRoute(
        path: '/about',
        pageBuilder: (_, state) => _page(state, const AboutPage()),
      ),
      if (kDebugMode)
        GoRoute(
          path: '/diagnostics',
          pageBuilder: (_, state) => _page(state, const DiagnosticsPage()),
        ),
    ],
  );
  ref.onDispose(router.dispose);
  return router;
});

/// A page with the platform's transitions: sliding in, and following the
/// predictive back gesture.
///
/// go_router's `builder:` can't be relied on for this: it looks for
/// material_ui's MaterialApp, not Flutter's own one that maxbo uses, and
/// without it gives pages no transition at all. The tabs' own pages are the
/// first in their navigators and never transition, so they keep `builder:`.
Page<void> _page(GoRouterState state, Widget child) =>
    MaterialPage(key: state.pageKey, child: child);

/// Shown when a page that needs data from the previous one is opened
/// without it.
class _MissingPage extends StatelessWidget {
  const _MissingPage();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(),
      body: const Center(child: Text('页面已失效，请返回重新打开')),
    );
  }
}

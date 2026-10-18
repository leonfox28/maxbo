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
import '../features/hot_search/hot_search_page.dart';
import '../features/media_viewer/image_viewer_page.dart';
import '../features/media_viewer/video_player_page.dart';
import '../features/notifications/notifications_page.dart';
import '../features/profile/me_page.dart';
import '../features/profile/profile_page.dart';
import '../features/settings/about_page.dart';
import '../features/settings/settings_page.dart';
import '../features/shell/home_shell.dart';
import '../features/status/status_detail_page.dart';
import '../features/timeline/timeline_page.dart';
import '../features/webview/web_page.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final router = GoRouter(
    routes: [
      StatefulShellRoute(
        builder: (_, _, shell) => shell,
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
              GoRoute(path: '/hot', builder: (_, _) => const HotSearchPage()),
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
        builder: (_, state) => StatusDetailPage(
          id: state.pathParameters['id']!,
          initial: state.extra is Status ? state.extra! as Status : null,
        ),
      ),
      GoRoute(
        path: '/comments/:id',
        builder: (_, state) => switch (state.extra) {
          final CommentThread thread => CommentRepliesPage(thread: thread),
          _ => const _MissingPage(),
        },
      ),
      GoRoute(
        path: '/video/:id',
        builder: (_, state) => VideoPlayerPage(
          id: state.pathParameters['id']!,
          index: int.tryParse(state.uri.queryParameters['i'] ?? ''),
          initial: state.extra is Status ? state.extra! as Status : null,
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
        builder: (_, state) => ProfilePage(
          uid: state.pathParameters['uid']!,
          initial: state.extra is WeiboUser ? state.extra! as WeiboUser : null,
        ),
      ),
      GoRoute(
        path: '/n/:name',
        builder: (_, state) =>
            ProfilePage(screenName: state.pathParameters['name']!),
      ),
      GoRoute(
        path: '/web',
        builder: (_, state) =>
            switch (Uri.tryParse(state.uri.queryParameters['url'] ?? '')) {
              final url? when url.isScheme('https') || url.isScheme('http') =>
                WebPage(url: url),
              _ => const _MissingPage(),
            },
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
      GoRoute(path: '/settings', builder: (_, _) => const SettingsPage()),
      GoRoute(path: '/about', builder: (_, _) => const AboutPage()),
      if (kDebugMode)
        GoRoute(
          path: '/diagnostics',
          builder: (_, _) => const DiagnosticsPage(),
        ),
    ],
  );
  ref.onDispose(router.dispose);
  return router;
});

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

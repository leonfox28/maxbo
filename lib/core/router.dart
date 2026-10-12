import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/hot_search/hot_search_page.dart';
import '../features/profile/me_page.dart';
import '../features/shell/home_shell.dart';
import '../features/timeline/timeline_page.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final router = GoRouter(
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => HomeShell(shell: shell),
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
            routes: [GoRoute(path: '/me', builder: (_, _) => const MePage())],
          ),
        ],
      ),
    ],
  );
  ref.onDispose(router.dispose);
  return router;
});

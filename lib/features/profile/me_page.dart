import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/session/session_controller.dart';
import '../../data/models/account.dart';
import '../../data/models/weibo_user.dart';
import '../../widgets/adaptive_layout.dart';
import '../auth/login_prompt.dart';

// No app bar: the tab bar names the tab, and what an app bar would offer
// is listed here.
class MePage extends ConsumerWidget {
  const MePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: switch (ref.watch(sessionProvider)) {
          // Settings stay within reach before logging in.
          SignedOut(:final expired) => ReadingList(
            builder: (context, padding) => ListView(
              padding: padding,
              children: [
                LoginPrompt(expired: expired),
                const Divider(),
                const _AppLinks(),
              ],
            ),
          ),
          SignedIn(:final account) => _AccountView(account: account),
        },
      ),
    );
  }
}

/// Settings, and diagnostics in debug builds.
class _AppLinks extends StatelessWidget {
  const _AppLinks();

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        ListTile(
          leading: const Icon(Icons.settings_outlined),
          title: const Text('设置'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => context.push('/settings'),
        ),
        if (kDebugMode)
          ListTile(
            leading: const Icon(Icons.bug_report_outlined),
            title: const Text('开发者诊断'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/diagnostics'),
          ),
      ],
    );
  }
}

class _AccountView extends ConsumerWidget {
  const _AccountView({required this.account});

  final Account account;

  Future<void> _confirmLogout(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('退出登录'),
        content: const Text('将清除本机保存的微博登录状态。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('退出'),
          ),
        ],
      ),
    );
    if (confirmed == true) await ref.read(sessionProvider.notifier).logout();
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ReadingList(
      builder: (context, padding) => ListView(
        padding: padding,
        children: [
          ListTile(
            leading: const CircleAvatar(child: Icon(Icons.person)),
            title: Text(account.screenName ?? '微博用户'),
            subtitle: Text('UID ${account.uid}'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push(
              '/u/${account.uid}',
              extra: switch (account.screenName) {
                final name? => WeiboUser(id: account.uid, screenName: name),
                null => null,
              },
            ),
          ),
          const Divider(),
          const _AppLinks(),
          ListTile(
            leading: const Icon(Icons.logout),
            title: const Text('退出登录'),
            onTap: () => _confirmLogout(context, ref),
          ),
        ],
      ),
    );
  }
}

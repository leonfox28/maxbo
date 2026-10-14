import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/session/session_controller.dart';
import '../../data/models/account.dart';
import '../../data/models/weibo_user.dart';
import '../auth/login_prompt.dart';

class MePage extends ConsumerWidget {
  const MePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('我'),
        actions: [
          if (kDebugMode)
            IconButton(
              tooltip: '开发者诊断',
              icon: const Icon(Icons.bug_report_outlined),
              onPressed: () => context.push('/diagnostics'),
            ),
          IconButton(
            tooltip: '设置',
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => context.push('/settings'),
          ),
        ],
      ),
      body: switch (ref.watch(sessionProvider)) {
        SignedOut(:final expired) => LoginPrompt(expired: expired),
        SignedIn(:final account) => _AccountView(account: account),
      },
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
    return ListView(
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
        ListTile(
          leading: const Icon(Icons.logout),
          title: const Text('退出登录'),
          onTap: () => _confirmLogout(context, ref),
        ),
      ],
    );
  }
}

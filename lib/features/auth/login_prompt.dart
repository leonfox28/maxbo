import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Shown in place of content that needs a signed-in user.
class LoginPrompt extends StatelessWidget {
  const LoginPrompt({super.key, this.expired = false});

  final bool expired;

  Future<void> _login(BuildContext context) async {
    final signedIn = await context.push<bool>('/login');
    if (signedIn == true && context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('登录成功')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.lock_outline,
              size: 48,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(height: 16),
            Text(
              expired ? '登录已过期，请重新登录' : '登录后即可浏览关注的人的微博',
              style: theme.textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: () => _login(context),
              child: const Text('登录微博'),
            ),
            const SizedBox(height: 16),
            Text(
              '登录在微博官方页面中完成，maxbo 不会接触你的密码，登录状态只保存在本机。',
              style: theme.textTheme.bodySmall,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/app_info.dart';
import '../../widgets/adaptive_layout.dart';

// Kept in line with the README.
const _disclaimer = [
  'maxbo 是非官方客户端，与新浪微博及北京微梦创科网络技术有限公司没有任何关联，仅供学习交流。',
  '不收费、不接受赞助，不提供去广告、批量下载、会员内容等功能。',
  '使用非官方客户端可能导致账号被风控或受到限制，风险由使用者自行承担。请遵守《微博服务使用协议》。',
  '如权利人认为本项目不妥，请到项目主页提 issue 联系，我们会及时处理。',
];

const _privacy = [
  '登录在微博官方网页登录页中完成，maxbo 接触不到你的密码。',
  '登录状态只保存在本机（Android Keystore），退出登录时清除。',
  '没有服务器，没有统计、埋点或崩溃上报，不收集任何数据；应用只和微博的服务器通信。',
  '只在你操作时请求微博，不在后台轮询或预取。',
];

/// What maxbo is, the disclaimer, the privacy statement and licences.
class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  Future<void> _open(BuildContext context, Uri url) async {
    final opened = await launchUrl(url, mode: LaunchMode.externalApplication);
    if (!opened && context.mounted) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('无法打开链接')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('关于')),
      body: ReadingList(
        padding: const EdgeInsets.symmetric(vertical: 16),
        builder: (context, padding) => ListView(
          padding: padding,
          children: [
            Center(
              child: Text(
                'maxbo',
                style: theme.textTheme.headlineMedium?.copyWith(
                  color: theme.colorScheme.primary,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            const SizedBox(height: 4),
            Center(
              child: Text(
                '版本 $appVersion',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            const SizedBox(height: 8),
            const Center(child: Text('开源、非商业的第三方微博阅读客户端')),
            const _Section(title: '声明', items: _disclaimer),
            const _Section(title: '隐私', items: _privacy),
            const Divider(height: 32),
            ListTile(
              leading: const Icon(Icons.code),
              title: const Text('项目主页'),
              subtitle: Text(repoUrl.toString()),
              onTap: () => _open(context, repoUrl),
            ),
            ListTile(
              leading: const Icon(Icons.feedback_outlined),
              title: const Text('问题反馈'),
              subtitle: const Text('在 GitHub 上提 issue'),
              onTap: () => _open(context, issuesUrl),
            ),
            ListTile(
              leading: const Icon(Icons.description_outlined),
              title: const Text('开源许可'),
              subtitle: const Text('maxbo 以 GPL-3.0 发布；以及所用的第三方库'),
              onTap: () => showLicensePage(
                context: context,
                applicationName: 'maxbo',
                applicationVersion: appVersion,
                applicationLegalese: '以 GNU GPL-3.0 许可发布',
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.items});

  final String title;
  final List<String> items;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: theme.textTheme.titleSmall?.copyWith(
              color: theme.colorScheme.primary,
            ),
          ),
          const SizedBox(height: 8),
          for (final item in items)
            Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('·  '),
                  Expanded(
                    child: Text(item, style: theme.textTheme.bodyMedium),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

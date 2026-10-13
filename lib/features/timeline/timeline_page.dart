import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/session/session_controller.dart';
import '../auth/login_prompt.dart';

class TimelinePage extends ConsumerWidget {
  const TimelinePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(title: const Text('首页')),
      body: switch (ref.watch(sessionProvider)) {
        SignedOut(:final expired) => LoginPrompt(expired: expired),
        SignedIn() => const Center(child: Text('关注时间线将在 M2 实现')),
      },
    );
  }
}

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../widgets/load_state.dart';
import '../../widgets/status_card/status_card.dart';
import '../../widgets/tab_reselect.dart';
import '../auth/login_prompt.dart';
import '../notifications/notices_controller.dart';
import '../status/status_actions.dart';
import 'timeline_controller.dart';

class TimelinePage extends ConsumerWidget {
  const TimelinePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // No app bar: the tab bar names the tab.
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: switch (ref.watch(sessionProvider)) {
          SignedOut(:final expired) => LoginPrompt(expired: expired),
          SignedIn() => const _Timeline(),
        },
      ),
    );
  }
}

class _Timeline extends ConsumerWidget {
  const _Timeline();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return switch (ref.watch(timelineProvider)) {
      AsyncData(:final value) => _StatusList(timeline: value),
      AsyncError(:final error) => ErrorRetry(
        error: error,
        onRetry: () => ref.invalidate(timelineProvider),
      ),
      AsyncLoading() => const Center(child: CircularProgressIndicator()),
    };
  }
}

class _StatusList extends ConsumerWidget {
  const _StatusList({required this.timeline});

  final TimelineState timeline;

  Future<void> _refresh(BuildContext context, WidgetRef ref) async {
    // The user asked for what's new; that includes notifications.
    unawaited(
      ref
          .read(unreadNoticesProvider.notifier)
          .check(minInterval: unreadRecheckInterval),
    );
    try {
      await ref.read(timelineProvider.notifier).refresh();
    } on WeiboException catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statuses = timeline.statuses;
    final actions = statusCardActions(context);
    // After a failure, loading more waits for the retry button.
    return LoadMoreTrigger(
      onNearEnd: timeline.loadMoreError == null
          ? ref.read(timelineProvider.notifier).loadMore
          : null,
      child: TabRefreshIndicator(
        onRefresh: () => _refresh(context, ref),
        child: statuses.isEmpty
            ? ListView(
                primary: true,
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 160),
                  Center(child: Text('还没有微博，下拉刷新试试')),
                ],
              )
            : ListView.separated(
                primary: true,
                physics: const AlwaysScrollableScrollPhysics(),
                itemCount: statuses.length + 1,
                separatorBuilder: (_, _) => const Divider(height: 1),
                itemBuilder: (context, i) => i < statuses.length
                    ? StatusCard(statuses[i], actions: actions)
                    : LoadMoreFooter(
                        loading: timeline.loadingMore,
                        hasMore: timeline.hasMore,
                        error: timeline.loadMoreError,
                        onLoadMore: () =>
                            ref.read(timelineProvider.notifier).loadMore(),
                      ),
              ),
      ),
    );
  }
}

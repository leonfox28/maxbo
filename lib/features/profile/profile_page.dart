import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/media/weibo_image.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/util/format.dart';
import '../../data/models/status.dart';
import '../../data/models/user_profile.dart';
import '../../data/models/weibo_user.dart';
import '../../widgets/adaptive_layout.dart';
import '../../widgets/load_state.dart';
import '../../widgets/status_card/status_card.dart';
import '../../widgets/tag_label.dart';
import '../../widgets/user_avatar.dart';
import '../status/status_actions.dart';
import 'profile_providers.dart';

/// A user's profile and statuses.
///
/// Opened from an avatar it has the uid and loads both at once; opened from
/// a mention it has only the name, so the statuses wait for the profile.
class ProfilePage extends ConsumerWidget {
  const ProfilePage({super.key, this.uid, this.screenName, this.initial})
    : assert(uid != null || screenName != null);

  final String? uid;
  final String? screenName;

  /// The user as the previous page showed them, shown until the profile
  /// arrives.
  final WeiboUser? initial;

  ProfileQuery get _query => uid != null
      ? (uid: uid, screenName: null)
      : (uid: null, screenName: screenName);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(profileProvider(_query));
    final user = profile.value?.user ?? initial;
    final uid = this.uid ?? user?.id;
    return Scaffold(
      appBar: AppBar(title: Text(user?.screenName ?? screenName ?? '')),
      body: uid != null
          ? _ProfileView(uid: uid, query: _query, initial: user)
          : profile.hasError
          ? ErrorRetry(
              error: profile.error,
              onRetry: () => ref.invalidate(profileProvider(_query)),
            )
          : const Center(child: CircularProgressIndicator()),
    );
  }
}

class _ProfileView extends ConsumerWidget {
  const _ProfileView({required this.uid, required this.query, this.initial});

  final String uid;
  final ProfileQuery query;
  final WeiboUser? initial;

  Future<void> _refresh(BuildContext context, WidgetRef ref) async {
    try {
      await Future.wait([
        ref.refresh(profileProvider(query).future),
        ref.refresh(userStatusesProvider(uid).future),
      ]);
    } on WeiboException catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(profileProvider(query));
    final provider = userStatusesProvider(uid);
    final statuses = ref.watch(provider);
    final base = statusCardActions(context);
    // This user's own avatar would only open this page again.
    final actions = base.copyWith(
      onUser: (user) {
        if (user.id != uid) base.onUser?.call(user);
      },
    );
    return RefreshIndicator(
      onRefresh: () => _refresh(context, ref),
      child: LoadMoreTrigger(
        onNearEnd: statuses.value?.loadMoreError == null
            ? ref.read(provider.notifier).loadMore
            : null,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            ReadingSlivers(
              slivers: [
                SliverToBoxAdapter(
                  child: _Header(
                    profile: profile,
                    initial: initial,
                    onRetry: () => ref.invalidate(profileProvider(query)),
                  ),
                ),
                const SliverToBoxAdapter(
                  child: Divider(height: 8, thickness: 8),
                ),
                PagedSliverList<Status>(
                  value: statuses,
                  itemBuilder: (context, status) =>
                      StatusCard(status, actions: actions),
                  dividerIndent: 0,
                  emptyText: '还没有微博',
                  onRetry: () => ref.invalidate(provider),
                  onLoadMore: () => ref.read(provider.notifier).loadMore(),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.profile, required this.onRetry, this.initial});

  final AsyncValue<UserProfile> profile;
  final WeiboUser? initial;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final p = profile.value;
    final user = p?.user ?? initial;
    final verified = user?.verifiedType;
    final cover = p?.coverUrl;
    final relation = switch (p) {
      UserProfile(following: true, followsMe: true) => '互相关注',
      UserProfile(following: true) => '已关注',
      UserProfile(followsMe: true) => '关注了你',
      _ => null,
    };

    final details = <Widget>[
      if (p?.verifiedReason case final reason?)
        Text(
          '微博认证：$reason',
          style: theme.textTheme.bodySmall?.copyWith(color: muted),
        ),
      if (p?.description case final description?) Text(description),
      if (p != null && (p.gender != null || p.location != null))
        Row(
          children: [
            if (p.gender == 'm')
              Icon(Icons.male, size: 16, color: Colors.blue.shade400)
            else if (p.gender == 'f')
              Icon(Icons.female, size: 16, color: Colors.pink.shade300),
            if (p.location case final location?) ...[
              const SizedBox(width: 4),
              Text(
                location,
                style: theme.textTheme.bodySmall?.copyWith(color: muted),
              ),
            ],
          ],
        ),
      if (p == null && profile.hasError)
        Row(
          children: [
            Expanded(
              child: Text(
                '资料加载失败：${loadErrorMessage(profile.error)}',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.error,
                ),
              ),
            ),
            TextButton(onPressed: onRetry, child: const Text('重试')),
          ],
        ),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (cover != null)
          SizedBox(
            height: 120,
            width: double.infinity,
            child: WeiboImage(cover),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  UserAvatar(user, size: 64),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Flexible(
                              child: Text(
                                user?.screenName ?? '',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.titleLarge,
                              ),
                            ),
                            if (VerifiedIcon.shows(verified)) ...[
                              const SizedBox(width: 4),
                              VerifiedIcon(verified!, size: 18),
                            ],
                            if (relation != null) ...[
                              const SizedBox(width: 8),
                              TagLabel(relation),
                            ],
                          ],
                        ),
                        const SizedBox(height: 6),
                        if (p != null)
                          _Counts(profile: p)
                        else if (!profile.hasError)
                          const SizedBox(
                            width: 120,
                            child: LinearProgressIndicator(),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
              for (final detail in details) ...[
                const SizedBox(height: 8),
                detail,
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// Following, follower and status counts. Display only: follower lists are
/// not part of v1.
class _Counts extends StatelessWidget {
  const _Counts({required this.profile});

  final UserProfile profile;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final counts = [
      for (final (label, count) in [
        ('关注', profile.friendsCount),
        ('粉丝', profile.followersCount),
        ('微博', profile.statusesCount),
      ])
        if (count != null) (label, count),
    ];
    return Text.rich(
      TextSpan(
        style: theme.textTheme.bodyMedium?.copyWith(color: muted),
        children: [
          for (final (i, (label, count)) in counts.indexed) ...[
            if (i > 0) const TextSpan(text: '    '),
            TextSpan(
              text: formatCount(count),
              style: TextStyle(
                color: theme.colorScheme.onSurface,
                fontWeight: FontWeight.w600,
              ),
            ),
            TextSpan(text: ' $label'),
          ],
        ],
      ),
    );
  }
}

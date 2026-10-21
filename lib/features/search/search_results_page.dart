import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/media/weibo_image.dart';
import '../../core/net/weibo_exception.dart';
import '../../core/util/format.dart';
import '../../data/models/status.dart';
import '../../data/models/topic.dart';
import '../../widgets/adaptive_layout.dart';
import '../../widgets/load_state.dart';
import '../../widgets/rich_text/status_text_parser.dart';
import '../../widgets/status_card/status_card.dart';
import '../interactions/delete_status.dart';
import '../status/status_actions.dart';
import '../webview/web_page.dart';
import 'search_results_providers.dart';

/// Where the posts found for [query] (e.g. `#话题#`) are routed.
String searchResultsLocation(String query) =>
    Uri(path: '/results', queryParameters: {'q': query}).toString();

/// Opens the posts found for [query].
Future<void> openSearchResults(BuildContext context, String query) =>
    context.push(searchResultsLocation(query));

/// The posts across Weibo that match a query, newest first, as cards like
/// the timeline's. A topic's lead and counts head its posts.
///
/// Weibo's own results page, ranked and split into tabs (users, videos, …),
/// is a tap away in the in-app browser.
class SearchResultsPage extends ConsumerWidget {
  const SearchResultsPage({super.key, required this.query});

  final String query;

  Future<void> _refresh(BuildContext context, WidgetRef ref) async {
    try {
      ref.invalidate(searchResultsProvider(query));
      await ref.read(searchResultsProvider(query).future);
    } on WeiboException catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final provider = searchResultsProvider(query);
    final results = ref.watch(provider);
    final controller = ref.read(provider.notifier);
    final topic = results.hasValue ? controller.topic : null;
    final base = statusCardActions(context);
    final actions = base.copyWith(
      // The topic searched for would only open this page again.
      onLink: (link) {
        if (link is TopicLink && '#${link.topic}#' == query) return;
        base.onLink?.call(link);
      },
      onDelete: (status) => deleteOwnStatus(
        context,
        status,
        onDeleted: () => controller.remove(status.id),
      ),
    );
    return Scaffold(
      appBar: AppBar(
        title: Text(query, maxLines: 1, overflow: TextOverflow.ellipsis),
        actions: [
          IconButton(
            tooltip: '网页版搜索结果',
            icon: const Icon(Icons.language),
            onPressed: () => openWebPage(context, weiboSearchUrl(query)),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => _refresh(context, ref),
        child: LoadMoreTrigger(
          onNearEnd: results.value?.loadMoreError == null
              ? controller.loadMore
              : null,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              ReadingSlivers(
                slivers: [
                  if (topic != null) ...[
                    SliverToBoxAdapter(child: _TopicHeader(topic)),
                    // A line like those between the statuses.
                    const SliverToBoxAdapter(child: Divider(height: 1)),
                  ],
                  PagedSliverList<Status>(
                    value: results,
                    itemBuilder: (context, status) =>
                        StatusCard(status, actions: actions),
                    dividerIndent: 0,
                    emptyText: '没有找到相关微博',
                    onRetry: () => ref.invalidate(provider),
                    onLoadMore: controller.loadMore,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TopicHeader extends StatelessWidget {
  const _TopicHeader(this.topic);

  final Topic topic;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final image = topic.imageUrl;
    final summary = topic.summary;
    final counts = [
      for (final (count, label) in [
        (topic.reads, '阅读'),
        (topic.mentions, '讨论'),
        (topic.authors, '原创人数'),
      ])
        if (count != null && count > 0) '${formatCount(count)}$label',
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (image != null) ...[
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: WeiboImage(image, width: 56, height: 56),
                ),
                const SizedBox(width: 14),
              ],
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '#${topic.name}#',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    if (counts.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        counts.join(' · '),
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: muted,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
          if (summary != null) ...[
            const SizedBox(height: 12),
            Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: '导语：',
                    style: TextStyle(color: muted),
                  ),
                  TextSpan(text: summary),
                ],
              ),
              style: theme.textTheme.bodyMedium,
            ),
          ],
        ],
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/net/weibo_exception.dart';
import '../core/paging/paged_controller.dart';
import '../features/webview/web_page.dart';

/// The message to show for a failed load.
String loadErrorMessage(Object? error) =>
    error is WeiboException ? error.message : '加载失败';

/// Calls [onNearEnd] as scrolling in [child] gets close to its end, to load
/// the next page before the user reaches it.
class LoadMoreTrigger extends StatelessWidget {
  const LoadMoreTrigger({
    super.key,
    required this.onNearEnd,
    required this.child,
  });

  final VoidCallback? onNearEnd;
  final Widget child;

  static const _extent = 800.0;

  @override
  Widget build(BuildContext context) {
    return NotificationListener<ScrollUpdateNotification>(
      onNotification: (n) {
        if (n.depth == 0 && n.metrics.extentAfter < _extent) onNearEnd?.call();
        return false;
      },
      child: child,
    );
  }
}

/// A paged list as a sliver: its items separated by dividers and ended by a
/// [LoadMoreFooter], or a spinner or the error while the first page loads.
class PagedSliverList<T> extends StatelessWidget {
  const PagedSliverList({
    super.key,
    required this.value,
    required this.itemBuilder,
    required this.onRetry,
    required this.onLoadMore,
    this.emptyText = '暂无内容',
    this.dividerIndent = 58,
  });

  final AsyncValue<PagedState<T>> value;
  final Widget Function(BuildContext context, T item) itemBuilder;
  final VoidCallback onRetry;
  final VoidCallback onLoadMore;
  final String emptyText;

  /// Lines items up with the text of a comment-like tile by default.
  final double dividerIndent;

  @override
  Widget build(BuildContext context) {
    final state = value.value;
    if (state == null) {
      return SliverToBoxAdapter(
        child: value.hasError
            ? ErrorRetry(error: value.error, onRetry: onRetry)
            : const Padding(
                padding: EdgeInsets.all(32),
                child: Center(child: CircularProgressIndicator()),
              ),
      );
    }
    final items = state.items;
    if (items.isEmpty && !state.hasMore) {
      final theme = Theme.of(context);
      return SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 40),
          child: Center(
            child: Text(
              emptyText,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ),
      );
    }
    return SliverList.separated(
      itemCount: items.length + 1,
      separatorBuilder: (_, _) => Divider(height: 1, indent: dividerIndent),
      itemBuilder: (context, i) => i < items.length
          ? itemBuilder(context, items[i])
          : LoadMoreFooter(
              loading: state.loadingMore,
              hasMore: state.hasMore,
              error: state.loadMoreError,
              onLoadMore: onLoadMore,
            ),
    );
  }
}

/// The end of a paged list: a spinner while the next page loads, the error
/// and a retry button when it failed, or a note that nothing is left.
class LoadMoreFooter extends StatelessWidget {
  const LoadMoreFooter({
    super.key,
    required this.loading,
    required this.hasMore,
    required this.onLoadMore,
    this.error,
    this.endText = '没有更多了',
  });

  final bool loading;
  final bool hasMore;
  final VoidCallback onLoadMore;
  final WeiboException? error;
  final String endText;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final error = this.error;
    final Widget child;
    if (loading) {
      child = const SizedBox.square(
        dimension: 24,
        child: CircularProgressIndicator(strokeWidth: 2),
      );
    } else if (error != null) {
      child = Column(
        children: [
          Text(error.message, style: theme.textTheme.bodySmall),
          if (error case CaptchaRequiredException(:final url))
            TextButton(
              onPressed: () => _verifyThenRetry(context, url, onLoadMore),
              child: const Text('去验证'),
            )
          else
            TextButton(onPressed: onLoadMore, child: const Text('重试')),
        ],
      );
    } else if (hasMore) {
      // Normally replaced by the spinner before it scrolls into view; kept
      // for lists too short to scroll.
      child = TextButton(onPressed: onLoadMore, child: const Text('加载更多'));
    } else {
      child = Text(
        endText,
        style: theme.textTheme.bodySmall?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 20),
      child: Center(child: child),
    );
  }
}

/// A failure in place of content, with a retry button; when Weibo wants a
/// security check first, a button to take it.
class ErrorRetry extends StatelessWidget {
  const ErrorRetry({super.key, required this.error, required this.onRetry});

  final Object? error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(loadErrorMessage(error), textAlign: TextAlign.center),
            const SizedBox(height: 16),
            if (error case CaptchaRequiredException(:final url))
              FilledButton(
                onPressed: () => _verifyThenRetry(context, url, onRetry),
                child: const Text('去验证'),
              )
            else
              FilledButton.tonal(onPressed: onRetry, child: const Text('重试')),
          ],
        ),
      ),
    );
  }
}

/// Tells the user why something they did failed; when Weibo wants a
/// security check first, offers to take it (they then try again themselves).
void showErrorSnackBar(BuildContext context, Object error) {
  // The snack bar can outlive [context], e.g. a list item scrolled away.
  final router = GoRouter.maybeOf(context);
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        content: Text(error is WeiboException ? error.message : '操作失败'),
        action: error is CaptchaRequiredException && router != null
            ? SnackBarAction(
                label: '去验证',
                onPressed: () => router.push(webPageLocation(error.url)),
              )
            : null,
      ),
    );
}

/// Lets the user take Weibo's security check at [url] in the in-app browser
/// (never automatically, PLAN §2), then calls [retry].
Future<void> _verifyThenRetry(
  BuildContext context,
  Uri url,
  VoidCallback retry,
) async {
  await openWebPage(context, url);
  retry();
}

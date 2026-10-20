import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/net/weibo_exception.dart';
import '../../core/session/session_controller.dart';
import '../../core/util/format.dart';
import '../../data/api/weibo_api.dart';
import '../../data/models/hot_search.dart';
import '../../widgets/adaptive_layout.dart';
import '../../widgets/load_state.dart';
import '../../widgets/tab_reselect.dart';
import '../../widgets/tag_label.dart';
import '../auth/login_prompt.dart';
import '../webview/web_page.dart';

/// Loaded when the tab is first opened, and again only on pull to refresh.
final hotSearchProvider = FutureProvider.autoDispose<HotSearch>(
  (ref) => ref.read(weiboApiProvider).fetchHotSearch(),
);

/// The search tab: a search bar over the hot search list. A search, as a
/// hot search entry, opens Weibo's results in the in-app browser.
class SearchPage extends ConsumerWidget {
  const SearchPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // No app bar: the tab bar names the tab.
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: switch (ref.watch(sessionProvider)) {
          SignedOut(:final expired) => LoginPrompt(expired: expired),
          SignedIn() => const Column(
            children: [
              _SearchField(),
              Expanded(child: _HotSearchList()),
            ],
          ),
        },
      ),
    );
  }
}

class _SearchField extends StatefulWidget {
  const _SearchField();

  @override
  State<_SearchField> createState() => _SearchFieldState();
}

class _SearchFieldState extends State<_SearchField> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _search() {
    final query = _controller.text.trim();
    if (query.isEmpty) return;
    // Or the keyboard comes back with the field when the results close.
    FocusManager.instance.primaryFocus?.unfocus();
    openWebPage(context, weiboSearchUrl(query));
  }

  @override
  Widget build(BuildContext context) {
    // Over the list's column, not spread across a wide window.
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: readingWidth),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
        child: SearchBar(
          controller: _controller,
          hintText: '搜索微博',
          leading: const Icon(Icons.search),
          trailing: [
            ListenableBuilder(
              listenable: _controller,
              builder: (context, _) => _controller.text.isEmpty
                  ? const SizedBox.shrink()
                  // Part of the field: tapping it keeps the keyboard up.
                  : TextFieldTapRegion(
                      child: IconButton(
                        onPressed: _controller.clear,
                        tooltip: '清除',
                        icon: const Icon(Icons.close),
                      ),
                    ),
            ),
          ],
          elevation: const WidgetStatePropertyAll(0),
          textInputAction: TextInputAction.search,
          onSubmitted: (_) => _search(),
          // Such as on the tab bar, whose other tabs have no keyboard.
          onTapOutside: (_) => FocusManager.instance.primaryFocus?.unfocus(),
        ),
      ),
    );
  }
}

class _HotSearchList extends ConsumerWidget {
  const _HotSearchList();

  Future<void> _refresh(BuildContext context, WidgetRef ref) async {
    try {
      ref.invalidate(hotSearchProvider);
      await ref.read(hotSearchProvider.future);
    } on WeiboException catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final value = ref.watch(hotSearchProvider);
    final hot = value.value;
    if (hot == null) {
      return value.hasError
          ? ErrorRetry(
              error: value.error,
              onRetry: () => ref.invalidate(hotSearchProvider),
            )
          : const Center(child: CircularProgressIndicator());
    }
    final pinned = hot.pinned.length;
    return TabRefreshIndicator(
      onRefresh: () => _refresh(context, ref),
      child: ReadingList(
        builder: (context, padding) => ListView.builder(
          primary: true,
          padding: padding,
          physics: const AlwaysScrollableScrollPhysics(),
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          itemCount: pinned + hot.items.length,
          itemBuilder: (context, i) => i < pinned
              ? _HotSearchTile(hot.pinned[i], pinned: true)
              : _HotSearchTile(hot.items[i - pinned]),
        ),
      ),
    );
  }
}

class _HotSearchTile extends StatelessWidget {
  const _HotSearchTile(this.item, {this.pinned = false});

  final HotSearchItem item;
  final bool pinned;

  // Weibo's colours for the top three.
  static const _topColors = [
    Color(0xFFF5222D),
    Color(0xFFFF6A00),
    Color(0xFFFAA90E),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final rank = item.rank;
    final badge = item.badge;
    final badgeColor = item.badgeColor;
    final heat = item.heat;

    final Widget leading;
    if (pinned) {
      leading = Icon(Icons.vertical_align_top, size: 20, color: _topColors[0]);
    } else if (rank != null) {
      leading = Text(
        '$rank',
        style: theme.textTheme.titleMedium?.copyWith(
          fontWeight: FontWeight.w600,
          color: rank <= 3 ? _topColors[rank - 1] : muted,
        ),
      );
    } else {
      // Inserted by Weibo outside the ranking.
      leading = Icon(Icons.trending_up, size: 18, color: muted);
    }

    return InkWell(
      onTap: () => openWebPage(context, weiboSearchUrl(item.query)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 13, 16, 13),
        child: Row(
          children: [
            SizedBox(width: 32, child: Center(child: leading)),
            const SizedBox(width: 8),
            Expanded(
              child: Row(
                children: [
                  Flexible(
                    child: Text(
                      item.word,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodyLarge,
                    ),
                  ),
                  if (badge != null) ...[
                    const SizedBox(width: 6),
                    TagLabel(
                      badge,
                      color: badgeColor == null ? null : Color(badgeColor),
                    ),
                  ],
                ],
              ),
            ),
            if (heat != null && heat > 0) ...[
              const SizedBox(width: 12),
              Text(
                formatCount(heat),
                style: theme.textTheme.bodySmall?.copyWith(color: muted),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

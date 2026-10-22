package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.core.paging.ListLoad

/**
 * A paged list as items of a lazy list: its items separated by dividers and ended by a
 * [LoadMoreFooter], or a spinner or the error while the first page loads.
 *
 * @param dividerIndent lines items up with the text of a comment-like tile by default.
 */
fun <T> LazyListScope.pagedItems(
    load: ListLoad<T>,
    key: (T) -> Any,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    emptyText: String = "暂无内容",
    dividerIndent: Dp = 58.dp,
    itemContent: @Composable (T) -> Unit,
) {
    when (load) {
        ListLoad.Loading ->
            item(key = "paged-loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        is ListLoad.Failed ->
            item(key = "paged-error") { ErrorRetry(load.error, onRetry, Modifier.fillMaxWidth()) }
        is ListLoad.Ready -> {
            val list = load.list
            if (list.items.isEmpty() && !list.hasMore) {
                item(key = "paged-empty") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return
            }
            items(list.items, key = key) { item ->
                itemContent(item)
                HorizontalDivider(Modifier.padding(start = dividerIndent))
            }
            item(key = "paged-footer") {
                LoadMoreFooter(
                    loading = list.loadingMore,
                    hasMore = list.hasMore,
                    error = list.loadMoreError,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

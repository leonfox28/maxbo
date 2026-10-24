package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.core.net.CaptchaRequiredException
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route

/** The message to show for a failed load. */
fun loadErrorMessage(error: Throwable?): String = (error as? WeiboException)?.message ?: "加载失败"

/**
 * Calls [onNearEnd] as the user scrolls [state] close to its end, to load the next page before they
 * reach it. Only scrolling triggers it, so a list too short to scroll waits for its footer's
 * button.
 */
@Composable
fun LoadMoreTrigger(state: LazyListState, onNearEnd: (() -> Unit)?) {
    val nearEnd by rememberUpdatedState(onNearEnd)
    val extent = with(LocalDensity.current) { 800.dp.toPx() }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect {
                val info = state.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull() ?: return@collect
                val pastViewport = last.offset + last.size - info.viewportEndOffset
                val itemsAfter = info.totalItemsCount - 1 - last.index
                val average = info.visibleItemsInfo.sumOf { it.size } / info.visibleItemsInfo.size
                if (pastViewport + itemsAfter * average < extent) nearEnd?.invoke()
            }
    }
}

/**
 * Keeps [state] at the very top when [items] change while it's there, as when a refresh brings
 * newer items. Left to itself, a lazy list keeps showing the item it showed first, which would
 * leave the new ones out of sight above it.
 *
 * The list's content has to get [items] by recomposition, as a value rather than a state it reads
 * itself: otherwise the list can measure them before this sees them.
 */
@Composable
fun KeepAtTop(state: LazyListState, items: List<*>?) {
    val shown = remember(state) { ShownItems(items) }
    // Applied before the list is measured with the new items, its position
    // still the one it had with the old.
    SideEffect {
        if (shown.items === items) return@SideEffect
        shown.items = items
        if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
            state.requestScrollToItem(0)
        }
    }
}

private class ShownItems(var items: List<*>?)

/**
 * The end of a paged list: a spinner while the next page loads, the error and a retry button when
 * it failed, or a note that nothing is left.
 */
@Composable
fun LoadMoreFooter(
    loading: Boolean,
    hasMore: Boolean,
    error: WeiboException?,
    onLoadMore: () -> Unit,
    endText: String = "没有更多了",
) {
    val navigator = LocalNavigator.current
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            error != null ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.message, style = MaterialTheme.typography.bodySmall)
                    if (error is CaptchaRequiredException) {
                        TextButton(onClick = { verifyThenRetry(navigator, error, onLoadMore) }) {
                            Text("去验证")
                        }
                    } else {
                        TextButton(onClick = onLoadMore) { Text("重试") }
                    }
                }
            // Normally replaced by the spinner before it scrolls into view;
            // kept for lists too short to scroll.
            hasMore -> TextButton(onClick = onLoadMore) { Text("加载更多") }
            else ->
                Text(
                    endText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

/**
 * A failure in place of content, with a retry button; when Weibo wants a security check first, a
 * button to take it.
 */
@Composable
fun ErrorRetry(error: Throwable?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(loadErrorMessage(error), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (error is CaptchaRequiredException) {
                Button(onClick = { verifyThenRetry(navigator, error, onRetry) }) { Text("去验证") }
            } else {
                FilledTonalButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

/**
 * Tells the user why something they did failed; when Weibo wants a security check first, offers to
 * take it (they then try again themselves).
 */
suspend fun SnackbarHostState.showError(error: Throwable, navigator: Navigator) {
    currentSnackbarData?.dismiss()
    val result =
        showSnackbar(
            message = (error as? WeiboException)?.message ?: "操作失败",
            actionLabel = if (error is CaptchaRequiredException) "去验证" else null,
            duration = SnackbarDuration.Short,
        )
    if (result == SnackbarResult.ActionPerformed && error is CaptchaRequiredException) {
        navigator.push(Route.Web(error.url.toString()))
    }
}

/**
 * Lets the user take Weibo's security check at the error's URL in the in-app browser (never
 * automatically, PLAN §2), then calls [retry] once they come back.
 */
private fun verifyThenRetry(
    navigator: Navigator,
    error: CaptchaRequiredException,
    retry: () -> Unit,
) {
    navigator.push(Route.Web(error.url.toString(), onClose = retry))
}

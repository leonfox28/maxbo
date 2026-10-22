package io.github.leonfox28.maxbo.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.core.paging.PagedState
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.ui.auth.LoginPrompt
import io.github.leonfox28.maxbo.ui.notifications.UNREAD_RECHECK_MS
import io.github.leonfox28.maxbo.ui.shell.LocalTabReselect
import io.github.leonfox28.maxbo.ui.status.rememberStatusCardActions
import io.github.leonfox28.maxbo.ui.widget.ErrorRetry
import io.github.leonfox28.maxbo.ui.widget.LoadMoreFooter
import io.github.leonfox28.maxbo.ui.widget.LoadMoreTrigger
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.status.StatusCard
import kotlinx.coroutines.launch

/** The home tab: the signed-in user's timeline. No app bar: the tab bar names the tab. */
@Composable
fun TimelinePage(padding: PaddingValues) {
    val session by LocalAppGraph.current.session.state.collectAsStateWithLifecycle()
    val area =
        Modifier.fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    when (val state = session) {
        is SessionState.SignedOut -> LoginPrompt(state.expired, area)
        is SessionState.SignedIn -> Timeline(state.account.uid, padding, area)
    }
}

@Composable
private fun Timeline(uid: String, padding: PaddingValues, modifier: Modifier) {
    val timeline = LocalAppGraph.current.timeline
    LaunchedEffect(uid) { timeline.start() }
    val state by timeline.state.collectAsStateWithLifecycle()
    when (val load = state) {
        ListLoad.Loading ->
            Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is ListLoad.Failed ->
            ErrorRetry(load.error, onRetry = timeline::reload, modifier = modifier)
        is ListLoad.Ready -> StatusList(load.list, padding, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusList(timeline: PagedState<Status>, padding: PaddingValues, modifier: Modifier) {
    val controller = LocalAppGraph.current.timeline
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val actions = rememberStatusCardActions()
    var refreshing by remember { mutableStateOf(false) }
    val unread = LocalAppGraph.current.unread
    fun refresh() {
        if (refreshing) return
        // The user asked for what's new; that includes notifications.
        unread.requestCheck(UNREAD_RECHECK_MS)
        refreshing = true
        scope.launch {
            try {
                controller.refresh()
            } catch (e: WeiboException) {
                snackbars.currentSnackbarData?.dismiss()
                launch { snackbars.showSnackbar(e.message) }
            } finally {
                refreshing = false
            }
        }
    }

    // Tapping the open tab again: back to the top, or a refresh from there.
    val reselects = LocalTabReselect.current
    LaunchedEffect(reselects) {
        reselects.collect {
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                listState.animateScrollToItem(0)
            } else {
                refresh()
            }
        }
    }
    // After a failure, loading more waits for the retry button.
    LoadMoreTrigger(
        listState,
        onNearEnd =
            if (timeline.loadMoreError == null) ({ scope.launch { controller.loadMore() } })
            else null,
    )
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = ::refresh, modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentPadding = readingPadding(maxWidth, padding, feed = true)
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
                if (timeline.items.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("还没有微博，下拉刷新试试")
                        }
                    }
                } else {
                    items(timeline.items, key = { it.id }) { status ->
                        StatusCard(status, actions = actions)
                        HorizontalDivider()
                    }
                    item(key = "footer") {
                        LoadMoreFooter(
                            loading = timeline.loadingMore,
                            hasMore = timeline.hasMore,
                            error = timeline.loadMoreError,
                            onLoadMore = { scope.launch { controller.loadMore() } },
                        )
                    }
                }
            }
        }
    }
}

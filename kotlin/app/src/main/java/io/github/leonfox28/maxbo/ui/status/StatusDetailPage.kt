package io.github.leonfox28.maxbo.ui.status

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.core.util.formatCount
import io.github.leonfox28.maxbo.core.util.formatPostTime
import io.github.leonfox28.maxbo.data.model.CommentOrder
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.ui.comments.CommentThread
import io.github.leonfox28.maxbo.ui.comments.CommentTile
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.BackButton
import io.github.leonfox28.maxbo.ui.widget.BarsOnScroll
import io.github.leonfox28.maxbo.ui.widget.CommentBar
import io.github.leonfox28.maxbo.ui.widget.ErrorRetry
import io.github.leonfox28.maxbo.ui.widget.HidingAppBar
import io.github.leonfox28.maxbo.ui.widget.LoadMoreTrigger
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.UserAvatar
import io.github.leonfox28.maxbo.ui.widget.loadErrorMessage
import io.github.leonfox28.maxbo.ui.widget.pagedItems
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.showError
import io.github.leonfox28.maxbo.ui.widget.status.StatusCard
import io.github.leonfox28.maxbo.ui.widget.status.StatusCardActions
import io.github.leonfox28.maxbo.ui.widget.text.StatusText
import kotlinx.coroutines.launch

private const val TITLE = "微博正文"

/**
 * A status with its full text, and its comments and reposts in tabs.
 *
 * [initial] (the status as the previous page showed it) shows until the full one arrives.
 */
@Composable
fun StatusDetailPage(id: String, initial: Status?) {
    val api = LocalAppGraph.current.api
    val model = viewModel { StatusDetailViewModel(id, api) }
    val load by model.load.collectAsStateWithLifecycle()
    val status = load.status ?: initial
    if (status == null) {
        Loading(load.error, onRetry = model::reload)
    } else {
        Detail(model, status, loadFailed = load.status == null && load.error != null, load.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Loading(error: WeiboException?, onRetry: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(TITLE) }, navigationIcon = { BackButton() }) }) {
        padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (error != null) ErrorRetry(error, onRetry) else CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun Detail(
    model: StatusDetailViewModel,
    status: Status,
    loadFailed: Boolean,
    error: WeiboException?,
) {
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { BarsOnScroll.Threshold.toPx() }
    val bars = remember(threshold) { BarsOnScroll(threshold) }
    val hidden by
        animateFloatAsState(if (bars.visible) 0f else 1f, BarsOnScroll.spec(), label = "bars")
    val base = rememberStatusCardActions()
    // Its own "展开" stays inert: the full text is shown or on its way.
    val cardActions =
        remember(base, status.id) {
            base.copy(onExpand = { if (it.id != status.id) base.onExpand?.invoke(it) })
        }
    val authorId = status.user?.id
    var refreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val comments = authorId?.let { model.comments(status, it, model.order) }
    val reposts = if (model.tab == DetailTab.Reposts) model.reposts(status) else null
    val list = if (model.tab == DetailTab.Reposts) reposts else comments
    // Each list's own state, keyed on the list, so switching lists (or
    // orders) never shows one's state as another's, not even for a frame.
    val commentsLoad =
        key(comments) { comments?.state?.collectAsStateWithLifecycle()?.value } ?: ListLoad.Loading
    val repostsLoad =
        key(reposts) { reposts?.state?.collectAsStateWithLifecycle()?.value } ?: ListLoad.Loading
    val listLoad: ListLoad<*> = if (model.tab == DetailTab.Reposts) repostsLoad else commentsLoad

    LoadMoreTrigger(
        listState,
        onNearEnd =
            list?.let { controller ->
                val ready = listLoad as? ListLoad.Ready
                if (ready?.list?.loadMoreError == null) ({ scope.launch { controller.loadMore() } })
                else null
            },
    )

    Scaffold(
        Modifier.nestedScroll(bars.connection),
        bottomBar = {
            if (status.user != null) {
                BottomBar(
                    status,
                    Modifier.graphicsLayer { translationY = hidden * 1.5f * size.height },
                )
            }
        },
        snackbarHost = { AppSnackbarHost() },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
        ) {
            HidingAppBar(bars.visible, title = { Text(TITLE) })
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        try {
                            model.refresh(status)
                        } catch (e: WeiboException) {
                            launch { snackbars.showError(e, navigator) }
                        } finally {
                            refreshing = false
                        }
                    }
                },
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        // Room to scroll the last item out from under the comment bar.
                        contentPadding =
                            readingPadding(
                                maxWidth,
                                PaddingValues(bottom = padding.calculateBottomPadding()),
                            ),
                    ) {
                        item(key = "status") {
                            StatusCard(status, detail = true, actions = cardActions)
                        }
                        if (loadFailed) {
                            item(key = "failed") {
                                LoadFailedBar(loadErrorMessage(error), onRetry = model::reload)
                            }
                        }
                        stickyHeader(key = "tabs") {
                            Tabs(
                                status,
                                tab = model.tab,
                                onTab = { model.tab = it },
                            )
                        }
                        when {
                            model.tab == DetailTab.Reposts && reposts != null ->
                                pagedItems(
                                    repostsLoad,
                                    key = { it.id },
                                    onRetry = reposts::reload,
                                    onLoadMore = { scope.launch { reposts.loadMore() } },
                                    emptyText = "还没有人转发",
                                ) {
                                    RepostTile(it, base)
                                }
                            comments != null -> {
                                item(key = "order") {
                                    OrderSwitch(model.order) { model.order = it }
                                }
                                pagedItems(
                                    commentsLoad,
                                    key = { it.id },
                                    onRetry = comments::reload,
                                    onLoadMore = { scope.launch { comments.loadMore() } },
                                    emptyText = "还没有评论",
                                ) { comment ->
                                    CommentTile(
                                        comment,
                                        authorId = authorId,
                                        actions = base,
                                        onThread = {
                                            navigator.push(
                                                Route.Comments(
                                                    CommentThread(comment, status.id, authorId)
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                            else ->
                                item(key = "no-comments") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(40.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("无法加载这条微博的评论")
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The comment bar, with repost and like buttons: the counts are on the tabs above. Writing comes
 * over in K5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBar(status: Status, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    CommentBar(
        hint = "写评论…",
        onClick = {},
        modifier = modifier,
        trailing = {
            // No ink, as on a card's counts.
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Row {
                    TextButton(onClick = {}) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("转发", color = colors.onSurfaceVariant)
                    }
                    val color = if (status.liked) colors.primary else colors.onSurfaceVariant
                    TextButton(onClick = {}) {
                        Icon(
                            if (status.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = null,
                            tint = color,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (status.likesCount > 0) formatCount(status.likesCount) else "赞",
                            color = color,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun LoadFailedBar(message: String, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .background(colors.errorContainer)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "正文加载失败：$message",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onErrorContainer,
        )
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

/** The repost and comment tabs with their counts, and the like count. */
@Composable
private fun Tabs(status: Status, tab: DetailTab, onTab: (DetailTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(Modifier.height(43.dp), verticalAlignment = Alignment.CenterVertically) {
            TabButton("转发 ${formatCount(status.repostsCount)}", tab == DetailTab.Reposts) {
                onTab(DetailTab.Reposts)
            }
            TabButton("评论 ${formatCount(status.commentsCount)}", tab == DetailTab.Comments) {
                onTab(DetailTab.Comments)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "赞 ${formatCount(status.likesCount)}",
                modifier = Modifier.padding(end = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else null,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(20.dp, 3.dp)
                .background(
                    if (selected) colors.primary else Color.Transparent,
                    RoundedCornerShape(2.dp),
                )
        )
    }
}

@Composable
private fun OrderSwitch(order: CommentOrder, onChange: (CommentOrder) -> Unit) {
    Row(Modifier.padding(start = 10.dp, top = 4.dp, end = 10.dp)) {
        for ((value, label) in listOf(CommentOrder.Hot to "按热度", CommentOrder.Time to "按时间")) {
            val selected = value == order
            Text(
                label,
                modifier =
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .then(if (selected) Modifier else Modifier.clickable { onChange(value) })
                        .padding(6.dp),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else null,
            )
        }
    }
}

@Composable
private fun RepostTile(status: Status, actions: StatusCardActions) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onOpen = actions.onOpen
    Row(
        Modifier.fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable { onOpen(status) } else Modifier)
            .padding(16.dp, 12.dp)
    ) {
        UserAvatar(status.user, size = 32.dp, onClick = actions.onUser)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                status.user?.screenName ?: "微博用户",
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            StatusText(
                status.text,
                urls = status.urls,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = (15 * 1.45).sp,
                    ),
                onLink = actions.onLink,
            )
            status.createdAt?.let {
                Spacer(Modifier.height(6.dp))
                Text(formatPostTime(it), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

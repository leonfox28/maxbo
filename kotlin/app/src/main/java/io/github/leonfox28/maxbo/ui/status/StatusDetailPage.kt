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
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.core.util.formatCount
import io.github.leonfox28.maxbo.core.util.formatPostTime
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.CommentOrder
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.data.model.withReply
import io.github.leonfox28.maxbo.ui.comments.CommentThread
import io.github.leonfox28.maxbo.ui.comments.CommentTile
import io.github.leonfox28.maxbo.ui.comments.CommentsController
import io.github.leonfox28.maxbo.ui.interactions.LikeKind
import io.github.leonfox28.maxbo.ui.interactions.LikeTarget
import io.github.leonfox28.maxbo.ui.interactions.confirmDelete
import io.github.leonfox28.maxbo.ui.interactions.like
import io.github.leonfox28.maxbo.ui.interactions.rememberLike
import io.github.leonfox28.maxbo.ui.interactions.rememberStatusWrites
import io.github.leonfox28.maxbo.ui.interactions.showCommentComposer
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.BackButton
import io.github.leonfox28.maxbo.ui.widget.BarsOnScroll
import io.github.leonfox28.maxbo.ui.widget.CommentBar
import io.github.leonfox28.maxbo.ui.widget.ErrorRetry
import io.github.leonfox28.maxbo.ui.widget.HidingAppBar
import io.github.leonfox28.maxbo.ui.widget.LoadMoreTrigger
import io.github.leonfox28.maxbo.ui.widget.LocalOverlays
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.UserAvatar
import io.github.leonfox28.maxbo.ui.widget.loadErrorMessage
import io.github.leonfox28.maxbo.ui.widget.pagedItems
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.showError
import io.github.leonfox28.maxbo.ui.widget.showMessage
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
    val graph = LocalAppGraph.current
    val model = viewModel { StatusDetailViewModel(id, graph.api, graph.openLists) }
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
    val graph = LocalAppGraph.current
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val overlays = LocalOverlays.current
    val writes = rememberStatusWrites()
    val scope = rememberCoroutineScope()
    val session by graph.session.state.collectAsStateWithLifecycle()
    val me = (session as? SessionState.SignedIn)?.account?.uid
    val threshold = with(LocalDensity.current) { BarsOnScroll.Threshold.toPx() }
    val bars = remember(threshold) { BarsOnScroll(threshold) }
    val hidden by
        animateFloatAsState(if (bars.visible) 0f else 1f, BarsOnScroll.spec(), label = "bars")
    val base = rememberStatusCardActions()
    val cardActions =
        remember(base, status.id) {
            base.copy(
                // Its own "展开" stays inert: the full text is shown or on its way.
                onExpand = { if (it.id != status.id) base.onExpand?.invoke(it) },
                // The page has nothing left to show.
                onDelete = { deleted ->
                    writes.delete(deleted) {
                        if (
                            (navigator.backStack.lastOrNull() as? Route.StatusDetail)?.id ==
                                model.id
                        ) {
                            navigator.pop()
                        }
                    }
                },
            )
        }
    val authorId = status.user?.id

    fun editComments(edit: (CommentsController) -> Unit) {
        if (authorId != null) graph.openLists.editComments(status.id, authorId, edit)
    }

    fun comment() = scope.launch {
        val sent =
            overlays.showCommentComposer(status.id, "写评论…", model.drafts, "status") { text, pid ->
                val comment = graph.api.postComment(status.id, text, pid = pid)
                model.count(status, comments = 1)
                model.tab = DetailTab.Comments
                comment?.let { new -> editComments { it.prepend(new) } }
            }
        if (sent) snackbars.showMessage("评论成功", graph.appScope)
    }

    fun reply(target: Comment) = scope.launch {
        val name = target.user?.screenName
        val hint = if (name == null) "回复评论…" else "回复 @$name"
        val sent =
            overlays.showCommentComposer(status.id, hint, model.drafts, target.id) { text, pid ->
                val reply = graph.api.postComment(status.id, text, replyTo = target.id, pid = pid)
                // Weibo counts replies among a status's comments.
                model.count(status, comments = 1)
                editComments { list -> list.replace(target.id) { it.withReply(reply) } }
            }
        if (sent) snackbars.showMessage("回复成功", graph.appScope)
    }

    fun delete(comment: Comment) = scope.launch {
        if (!overlays.confirmDelete() || !model.deleting.add(comment.id)) return@launch
        try {
            graph.api.deleteComment(comment.id)
            model.count(status, comments = -1)
            editComments { it.remove(comment.id) }
            snackbars.showMessage("已删除", graph.appScope)
        } catch (e: WeiboException) {
            launch { snackbars.showError(e, navigator) }
        } finally {
            model.deleting -= comment.id
        }
    }
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
                    onComment = ::comment,
                    // The list of reposts, if open, gets the new one from the repost itself.
                    onRepost = { writes.repost(status) { model.count(status, reposts = 1) } },
                    modifier =
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
                                repostsCount = model.repostsCount(status),
                                commentsCount = model.commentsCount(status),
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
                                        onClick =
                                            if (comment.canReply) ({ reply(comment) }) else null,
                                        onLongClick =
                                            if (me != null && comment.user?.id == me) {
                                                { delete(comment) }
                                            } else null,
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

/** The comment bar, with repost and like buttons: the counts are on the tabs above. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBar(
    status: Status,
    onComment: () -> Unit,
    onRepost: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shown = rememberLike(LikeTarget(LikeKind.Status, status.id), status.like)
    val like = shown.like
    CommentBar(
        hint = "写评论…",
        onClick = onComment,
        modifier = modifier,
        trailing = {
            // No ink, as on a card's counts.
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Row {
                    TextButton(onClick = onRepost) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("转发", color = colors.onSurfaceVariant)
                    }
                    val color = if (like.liked) colors.primary else colors.onSurfaceVariant
                    TextButton(onClick = shown.onToggle ?: {}, enabled = shown.onToggle != null) {
                        Icon(
                            if (like.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = null,
                            tint = color,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (like.count > 0) formatCount(like.count) else "赞", color = color)
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

/**
 * The repost and comment tabs with their counts (with what the user added or deleted here), and the
 * like count (the like button is in the bottom bar).
 */
@Composable
private fun Tabs(
    status: Status,
    repostsCount: Int,
    commentsCount: Int,
    tab: DetailTab,
    onTab: (DetailTab) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val like = rememberLike(LikeTarget(LikeKind.Status, status.id), status.like).like
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(Modifier.height(43.dp), verticalAlignment = Alignment.CenterVertically) {
            TabButton("转发 ${formatCount(repostsCount)}", tab == DetailTab.Reposts) {
                onTab(DetailTab.Reposts)
            }
            TabButton("评论 ${formatCount(commentsCount)}", tab == DetailTab.Comments) {
                onTab(DetailTab.Comments)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "赞 ${formatCount(like.count)}",
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

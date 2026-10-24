package io.github.leonfox28.maxbo.ui.notifications

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.media.WeiboImage
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.core.paging.PagedController
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.core.util.formatPostTime
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.CommentNotice
import io.github.leonfox28.maxbo.data.model.LikeNotice
import io.github.leonfox28.maxbo.data.model.NoticeKind
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.ui.auth.LoginPrompt
import io.github.leonfox28.maxbo.ui.comments.CommentTile
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.shell.LocalTabReselect
import io.github.leonfox28.maxbo.ui.status.rememberStatusCardActions
import io.github.leonfox28.maxbo.ui.theme.UnreadColor
import io.github.leonfox28.maxbo.ui.widget.LoadMoreTrigger
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.ReadingWidth
import io.github.leonfox28.maxbo.ui.widget.UserAvatar
import io.github.leonfox28.maxbo.ui.widget.pagedItems
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.showError
import io.github.leonfox28.maxbo.ui.widget.status.StatusCard
import io.github.leonfox28.maxbo.ui.widget.status.StatusCardActions
import io.github.leonfox28.maxbo.ui.widget.text.plainStatusText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private val Tabs =
    listOf(
        NoticeKind.Comments to "评论",
        NoticeKind.MentionStatuses to "@我的微博",
        NoticeKind.MentionComments to "@我的评论",
        NoticeKind.Likes to "赞",
    )

/** The notification lists, each made when its tab is first shown and kept while the app runs. */
class NotificationsViewModel(private val api: WeiboApi) : ViewModel() {
    private val lists = mutableMapOf<NoticeKind, PagedController<*>>()

    /** [kind]'s list, loading its first page if it wasn't open. */
    fun list(kind: NoticeKind): PagedController<*> =
        lists.getOrPut(kind) {
            when (kind) {
                NoticeKind.Comments,
                NoticeKind.MentionComments -> CommentNoticesController(api, kind, viewModelScope)
                NoticeKind.MentionStatuses -> MentionStatusesController(api, viewModelScope)
                NoticeKind.Likes -> LikeNoticesController(api, viewModelScope)
            }.also { it.start() }
        }

    fun opened(kind: NoticeKind): PagedController<*>? = lists[kind]
}

/**
 * The notifications tab: comments, mentions and likes, one list each. No app bar: the tab bar names
 * the tab.
 *
 * A list loads when its tab is first shown, which marks its kind read on Weibo's side. Opening a
 * tab marked unread clears the mark, and reloads the list if it was loaded before.
 */
@Composable
fun NotificationsPage(padding: PaddingValues) {
    val session by LocalAppGraph.current.session.state.collectAsStateWithLifecycle()
    val area =
        Modifier.fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    when (val state = session) {
        is SessionState.SignedOut -> LoginPrompt(state.expired, area)
        is SessionState.SignedIn -> Notices(padding, area)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Notices(padding: PaddingValues, modifier: Modifier) {
    val graph = LocalAppGraph.current
    val api = graph.api
    val model = viewModel { NotificationsViewModel(api) }
    val unread by graph.unread.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState { Tabs.size }
    val scope = rememberCoroutineScope()
    // Only the shown list answers a tap on the open home tab.
    val taps = remember { Tabs.map { MutableSharedFlow<Unit>(extraBufferCapacity = 1) } }
    val homeTaps = LocalTabReselect.current
    LaunchedEffect(homeTaps) { homeTaps.collect { taps[pager.currentPage].tryEmit(Unit) } }

    // The user is looking at the open tab: clear its mark, and reload its
    // list if that came before whatever is new. The reload goes on in the
    // page's scope: clearing the mark restarts this effect.
    LaunchedEffect(pager.currentPage, unread) {
        val kind = Tabs[pager.currentPage].first
        if (!unread.has(kind)) return@LaunchedEffect
        graph.unread.markRead(kind)
        val list = model.opened(kind) ?: return@LaunchedEffect
        if (list.state.value is ListLoad.Ready) {
            scope.launch {
                try {
                    list.refresh()
                } catch (_: WeiboException) {}
            }
        }
    }

    Column(modifier) {
        // Over the lists' column, not spread across a wide window.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.widthIn(max = ReadingWidth)) {
                val widths = tabWidths(Tabs.map { it.second }, constraints.maxWidth)
                PrimaryScrollableTabRow(
                    pager.currentPage,
                    edgePadding = 0.dp,
                    minTabWidth = 0.dp,
                ) {
                    Tabs.forEachIndexed { i, (kind, label) ->
                        Tab(
                            selected = pager.currentPage == i,
                            onClick = { scope.launch { pager.animateScrollToPage(i) } },
                            modifier = Modifier.width(widths[i]),
                            text = { TabLabel(label, unread.has(kind)) },
                        )
                    }
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), key = { Tabs[it].first }) { page ->
            NoticeList(model, Tabs[page].first, padding, taps[page])
        }
    }
}

/** The space [Tab] leaves on either side of its label. */
private val TabLabelPadding = 16.dp

/**
 * How wide to make the tabs of a bar [width] pixels across, so that each label stays on one line:
 * an even share while every label fits in one, else each label's own width with what is left over
 * going to the narrowest. With large enough text, the tabs are wider than the bar, and it scrolls.
 */
@Composable
private fun tabWidths(labels: List<String>, width: Int): List<Dp> {
    val measurer = rememberTextMeasurer()
    // What Tab sets its label in.
    val style = MaterialTheme.typography.titleSmall
    val density = LocalDensity.current
    return remember(labels, width, measurer, style, density) {
        with(density) {
            val padding = TabLabelPadding.roundToPx() * 2
            val needs = labels.map {
                measurer.measure(it, style, maxLines = 1).size.width + padding
            }
            shareWidth(needs, width).map { it.toDp() }
        }
    }
}

/**
 * Widths for things needing [needs] each, out of [width]: each at least its need, with what is left
 * of [width] evening up the narrowest. Their needs alone if those come to [width] or more.
 */
internal fun shareWidth(needs: List<Int>, width: Int): List<Int> {
    if (needs.sum() >= width) return needs
    val widestFirst = needs.indices.sortedByDescending { needs[it] }
    // Set aside the widest while they need more than an even share of what is left.
    var rest = width
    var count = needs.size
    for (i in widestFirst) {
        if (needs[i] * count <= rest) break
        rest -= needs[i]
        count--
    }
    val widths = needs.toMutableList()
    widestFirst.takeLast(count).forEachIndexed { j, i ->
        widths[i] = rest / count + if (j < rest % count) 1 else 0
    }
    return widths
}

/** A tab's name, with a dot at its top right while it has something unread. */
@Composable
private fun TabLabel(label: String, unread: Boolean) {
    Box(
        Modifier.clearAndSetSemantics { contentDescription = if (unread) "$label，有未读" else label }
    ) {
        Text(label)
        if (unread) {
            // Not a Badge: its dot sits inside the corner, over the last glyph.
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = (-2).dp)
                    .size(6.dp)
                    .background(UnreadColor, CircleShape)
            )
        }
    }
}

/** One kind's list, with its own scrolling, pull to refresh, and tap on the open home tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeList(
    model: NotificationsViewModel,
    kind: NoticeKind,
    padding: PaddingValues,
    taps: MutableSharedFlow<Unit>,
) {
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()
    val actions = rememberStatusCardActions()
    val list = model.list(kind)
    val load = key(list) { list.state.collectAsStateWithLifecycle().value }
    val listState = rememberLazyListState()
    var refreshing by remember { mutableStateOf(false) }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                list.refresh()
            } catch (e: WeiboException) {
                launch { snackbars.showError(e, navigator) }
            } finally {
                refreshing = false
            }
        }
    }
    LaunchedEffect(taps) {
        taps.collect {
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                listState.animateScrollToItem(0)
            } else {
                refresh()
            }
        }
    }
    LoadMoreTrigger(
        listState,
        onNearEnd =
            if ((load as? ListLoad.Ready)?.list?.loadMoreError == null)
                ({ scope.launch { list.loadMore() } })
            else null,
    )
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = ::refresh) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = readingPadding(maxWidth, padding),
            ) {
                @Suppress("UNCHECKED_CAST")
                when (kind) {
                    NoticeKind.Comments,
                    NoticeKind.MentionComments ->
                        pagedItems(
                            load as ListLoad<CommentNotice>,
                            key = { it.id },
                            onRetry = list::reload,
                            onLoadMore = { scope.launch { list.loadMore() } },
                            emptyText = if (kind == NoticeKind.Comments) "还没有收到评论" else "还没有评论提到你",
                        ) {
                            CommentNoticeTile(it, actions)
                        }
                    NoticeKind.MentionStatuses ->
                        pagedItems(
                            load as ListLoad<Status>,
                            key = { it.id },
                            onRetry = list::reload,
                            onLoadMore = { scope.launch { list.loadMore() } },
                            emptyText = "还没有微博提到你",
                            dividerIndent = 0.dp,
                        ) {
                            StatusCard(it, actions = actions)
                        }
                    NoticeKind.Likes ->
                        pagedItems(
                            load as ListLoad<LikeNotice>,
                            key = { it.id },
                            onRetry = list::reload,
                            onLoadMore = { scope.launch { list.loadMore() } },
                            emptyText = "还没有收到赞",
                        ) {
                            LikeNoticeTile(it, actions)
                        }
                }
            }
        }
    }
}

/**
 * A comment to the user, above the comment it answers (if any) and the post it is on. Tapping it
 * opens the post.
 */
@Composable
private fun CommentNoticeTile(notice: CommentNotice, actions: StatusCardActions) {
    val status = notice.status
    val onOpen = actions.onOpen
    val open = if (status?.user == null || onOpen == null) null else ({ onOpen(status) })
    CommentTile(
        notice.comment,
        actions = actions,
        onClick = open,
        below = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                notice.replyTo?.let { CommentQuote(it) }
                status?.let { StatusQuote(it, open) }
            }
        },
    )
}

/** Who liked what, and when. Tapping it opens the post. */
@Composable
private fun LikeNoticeTile(notice: LikeNotice, actions: StatusCardActions) {
    val navigator = LocalNavigator.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val status = notice.status
    val comment = notice.comment
    val open: (() -> Unit)? =
        if (status?.user == null) null
        else if (notice.statusComplete) ({ actions.onOpen?.invoke(status) })
        // A cut-down post would show without its pictures and counts until
        // the detail page has loaded it.
        else ({ navigator.push(Route.StatusDetail(status.id)) })
    Row(
        Modifier.fillMaxWidth()
            .then(if (open != null) Modifier.clickable(onClick = open) else Modifier)
            .padding(16.dp, 12.dp)
    ) {
        UserAvatar(notice.user, size = 32.dp, onClick = actions.onUser)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Medium)) {
                        append(notice.user?.screenName ?: "微博用户")
                    }
                    append(if (comment != null) " 赞了你的评论" else " 赞了你的微博")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            notice.createdAt?.let {
                Spacer(Modifier.height(2.dp))
                Text(formatPostTime(it), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Spacer(Modifier.height(8.dp))
            if (comment != null) {
                CommentQuote(comment)
                Spacer(Modifier.height(6.dp))
            }
            status?.let { StatusQuote(it, open) }
        }
    }
}

/** A comment cited in a notification: its author and the start of its text. */
@Composable
private fun CommentQuote(comment: Comment) {
    val colors = MaterialTheme.colorScheme
    Text(
        buildAnnotatedString {
            comment.user?.let {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("${it.screenName}：") }
            }
            append(plainStatusText(comment.text))
        },
        modifier =
            Modifier.fillMaxWidth()
                .drawBehind {
                    drawLine(
                        colors.outlineVariant,
                        Offset(0f, 0f),
                        Offset(0f, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                .padding(start = 8.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
    )
}

private val Thumb = 48.dp

/** The post a notification is about, as a small card: a picture, its author and its text. */
@Composable
private fun StatusQuote(status: Status, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val settings by LocalAppGraph.current.settings.settings.collectAsStateWithLifecycle()
    val density = LocalDensity.current.density
    val user = status.user
    val thumb =
        status.pics
            .firstOrNull()
            ?.variantFor(settings.imageQuality.listPixels(Thumb.value, density))
            ?.url ?: status.video?.coverUrl
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(6.dp),
        color = colors.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (thumb != null) {
                WeiboImage(thumb, Modifier.size(Thumb).clip(RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
            }
            // Deleted or hidden posts come without an author.
            Text(
                if (user == null) {
                    buildAnnotatedString { append("这条微博已被删除或不可见") }
                } else {
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append("@${user.screenName}：")
                        }
                        append(plainStatusText(status.text))
                    }
                },
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

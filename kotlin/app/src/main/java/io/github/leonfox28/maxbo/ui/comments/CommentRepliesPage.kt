package io.github.leonfox28.maxbo.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.withReply
import io.github.leonfox28.maxbo.data.model.withoutReply
import io.github.leonfox28.maxbo.ui.interactions.CommentDraft
import io.github.leonfox28.maxbo.ui.interactions.confirmDelete
import io.github.leonfox28.maxbo.ui.interactions.showCommentComposer
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.status.rememberStatusCardActions
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.BackButton
import io.github.leonfox28.maxbo.ui.widget.CommentBar
import io.github.leonfox28.maxbo.ui.widget.LoadMoreTrigger
import io.github.leonfox28.maxbo.ui.widget.LocalOverlays
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.pagedItems
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.showError
import io.github.leonfox28.maxbo.ui.widget.showMessage
import kotlinx.coroutines.launch

/** The replies of a thread, loaded while its page is open, and what the user writes there. */
class RepliesViewModel(thread: CommentThread, api: WeiboApi) : ViewModel() {
    val replies =
        RepliesController(api, thread.root.id, thread.authorId, viewModelScope).also { it.start() }

    /** What the user is writing here, by what it replies to. */
    val drafts = mutableMapOf<String, CommentDraft>()

    /** Replies being deleted, so a second tap doesn't ask Weibo again. */
    val deleting = mutableSetOf<String>()
}

/** A comment and all the replies in its thread ("楼中楼"), where the user can reply to any of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentRepliesPage(thread: CommentThread) {
    val graph = LocalAppGraph.current
    val model = viewModel { RepliesViewModel(thread, graph.api) }
    val replies = model.replies
    val load by replies.state.collectAsStateWithLifecycle()
    val session by graph.session.state.collectAsStateWithLifecycle()
    val me = (session as? SessionState.SignedIn)?.account?.uid
    val actions = rememberStatusCardActions()
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val root = thread.root
    val total = (load as? ListLoad.Ready)?.list?.total ?: root.replyCount
    val rootName = root.user?.screenName

    // Shows a change of the thread on the status page below too.
    fun editRoot(change: (Comment) -> Comment) =
        graph.openLists.editComments(thread.statusId, thread.authorId) {
            it.replace(root.id, change)
        }

    fun reply(target: Comment) = scope.launch {
        val name = target.user?.screenName
        val hint = if (name == null) "回复评论…" else "回复 @$name"
        val sent =
            overlays.showCommentComposer(thread.statusId, hint, model.drafts, target.id) { text, pid
                ->
                val reply =
                    graph.api.postComment(thread.statusId, text, replyTo = target.id, pid = pid)
                // Replies list newest first.
                reply?.let(replies::prepend)
                editRoot { it.withReply(reply) }
            }
        if (sent) snackbars.showMessage("回复成功", graph.appScope)
    }

    fun delete(reply: Comment) = scope.launch {
        if (!overlays.confirmDelete() || !model.deleting.add(reply.id)) return@launch
        try {
            graph.api.deleteComment(reply.id)
            replies.remove(reply.id)
            editRoot { it.withoutReply(reply.id) }
            snackbars.showMessage("已删除", graph.appScope)
        } catch (e: WeiboException) {
            launch { snackbars.showError(e, navigator) }
        } finally {
            model.deleting -= reply.id
        }
    }

    LoadMoreTrigger(
        listState,
        onNearEnd =
            if ((load as? ListLoad.Ready)?.list?.loadMoreError == null)
                ({ scope.launch { replies.loadMore() } })
            else null,
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text("评论详情") }, navigationIcon = { BackButton() }) },
        bottomBar = {
            if (root.canReply) {
                CommentBar(
                    hint = if (rootName == null) "回复评论…" else "回复 @$rootName",
                    onClick = { reply(root) },
                )
            }
        },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = readingPadding(maxWidth, padding),
            ) {
                item(key = "root") {
                    CommentTile(
                        root,
                        authorId = thread.authorId,
                        actions = actions,
                        onClick = if (root.canReply) ({ reply(root) }) else null,
                    )
                }
                item(key = "gap") {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                }
                item(key = "total") {
                    Text(
                        "$total 条回复",
                        modifier =
                            Modifier.padding(
                                start = 16.dp,
                                top = 12.dp,
                                end = 16.dp,
                                bottom = 4.dp,
                            ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                pagedItems(
                    load,
                    key = { it.id },
                    onRetry = replies::reload,
                    onLoadMore = { scope.launch { replies.loadMore() } },
                    emptyText = "还没有回复",
                ) { item ->
                    CommentTile(
                        item,
                        authorId = thread.authorId,
                        actions = actions,
                        onClick = if (item.canReply) ({ reply(item) }) else null,
                        onLongClick =
                            if (me != null && item.user?.id == me) ({ delete(item) }) else null,
                    )
                }
            }
        }
    }
}

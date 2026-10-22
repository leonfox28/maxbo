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
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.ui.status.rememberStatusCardActions
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.BackButton
import io.github.leonfox28.maxbo.ui.widget.CommentBar
import io.github.leonfox28.maxbo.ui.widget.LoadMoreTrigger
import io.github.leonfox28.maxbo.ui.widget.pagedItems
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import kotlinx.coroutines.launch

/** The replies of a thread, loaded while its page is open. */
class RepliesViewModel(thread: CommentThread, api: WeiboApi) : ViewModel() {
    val replies =
        RepliesController(api, thread.root.id, thread.authorId, viewModelScope).also { it.start() }
}

/** A comment and all the replies in its thread ("楼中楼"). Replying to them comes over in K5. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentRepliesPage(thread: CommentThread) {
    val api = LocalAppGraph.current.api
    val model = viewModel { RepliesViewModel(thread, api) }
    val replies = model.replies
    val load by replies.state.collectAsStateWithLifecycle()
    val actions = rememberStatusCardActions()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val root = thread.root
    val total = (load as? ListLoad.Ready)?.list?.total ?: root.replyCount
    val rootName = root.user?.screenName

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
            if (root.canReply)
                CommentBar(hint = if (rootName == null) "回复评论…" else "回复 @$rootName", onClick = {})
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
                    CommentTile(root, authorId = thread.authorId, actions = actions)
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
                ) { reply ->
                    CommentTile(reply, authorId = thread.authorId, actions = actions)
                }
            }
        }
    }
}

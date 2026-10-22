package io.github.leonfox28.maxbo.ui.status

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.CommentOrder
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.ui.comments.CommentsController
import io.github.leonfox28.maxbo.ui.comments.RepostsController
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetailTab {
    Reposts,
    Comments,
}

/** The status as loaded for its page, or why it couldn't be. */
data class StatusLoad(val status: Status? = null, val error: WeiboException? = null)

/**
 * What a status's page holds while it is open: the full status, and the lists of comments and
 * reposts opened so far, which stay loaded, so going back to one doesn't fetch it again.
 *
 * Opening the page loads the status and its first page of comments; the reposts load only when
 * their tab is opened (PLAN §2).
 */
class StatusDetailViewModel(val id: String, private val api: WeiboApi) : ViewModel() {
    private val _load = MutableStateFlow(StatusLoad())
    val load: StateFlow<StatusLoad> = _load.asStateFlow()

    var tab by mutableStateOf(DetailTab.Comments)
    var order by mutableStateOf(CommentOrder.Hot)

    private val comments = mutableMapOf<Pair<String, CommentOrder>, CommentsController>()
    private var reposts: RepostsController? = null

    init {
        reload()
    }

    fun reload() {
        _load.update { it.copy(error = null) }
        viewModelScope.launch {
            try {
                _load.value = StatusLoad(api.fetchStatus(id))
            } catch (e: WeiboException) {
                _load.update { it.copy(error = e) }
            }
        }
    }

    /** The comments on [status] in [order], loading their first page if they weren't open. */
    fun comments(status: Status, authorId: String, order: CommentOrder): CommentsController =
        comments.getOrPut(authorId to order) {
            CommentsController(api, status.id, authorId, order, viewModelScope).also { it.start() }
        }

    fun reposts(status: Status): RepostsController =
        reposts
            ?: RepostsController(api, status.id, viewModelScope).also {
                reposts = it
                it.start()
            }

    /**
     * Loads the status and the list on show again.
     *
     * @throws WeiboException on failure, what was shown staying, for the caller to report.
     */
    suspend fun refresh(status: Status) = coroutineScope {
        val authorId = status.user?.id
        listOf(
                async { _load.value = StatusLoad(api.fetchStatus(id)) },
                async {
                    when {
                        tab == DetailTab.Reposts -> reposts(status).refresh()
                        authorId != null -> comments(status, authorId, order).refresh()
                    }
                },
            )
            .awaitAll()
        Unit
    }
}

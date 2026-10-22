package io.github.leonfox28.maxbo.ui.comments

import io.github.leonfox28.maxbo.core.paging.PagedController
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.CommentOrder
import io.github.leonfox28.maxbo.data.model.Paged
import io.github.leonfox28.maxbo.data.model.Status
import kotlinx.coroutines.CoroutineScope

/** The comments on status [statusId] by [authorId], in [order]. */
class CommentsController(
    private val api: WeiboApi,
    val statusId: String,
    val authorId: String,
    val order: CommentOrder,
    scope: CoroutineScope,
) : PagedController<Comment>(scope) {
    override suspend fun fetchPage(cursor: String?): Paged<Comment> =
        api.fetchComments(statusId = statusId, authorId = authorId, order = order, maxId = cursor)

    override fun idOf(item: Comment) = item.id
}

/** The replies to comment [commentId] on a status by [authorId]. */
class RepliesController(
    private val api: WeiboApi,
    val commentId: String,
    val authorId: String,
    scope: CoroutineScope,
) : PagedController<Comment>(scope) {
    override suspend fun fetchPage(cursor: String?): Paged<Comment> =
        api.fetchReplies(commentId = commentId, authorId = authorId, maxId = cursor)

    override fun idOf(item: Comment) = item.id
}

/** The reposts of status [statusId], paged by number. */
class RepostsController(private val api: WeiboApi, val statusId: String, scope: CoroutineScope) :
    PagedController<Status>(scope) {
    override suspend fun fetchPage(cursor: String?): Paged<Status> =
        api.fetchReposts(statusId = statusId, page = cursor?.toInt() ?: 1)

    override fun idOf(item: Status) = item.id
}

/**
 * What the replies page shows: the thread of [root], on status [statusId] by [authorId] (Weibo's
 * comment API asks for the author).
 */
data class CommentThread(val root: Comment, val statusId: String, val authorId: String)

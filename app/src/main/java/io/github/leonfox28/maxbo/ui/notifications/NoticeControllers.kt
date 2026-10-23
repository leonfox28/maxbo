package io.github.leonfox28.maxbo.ui.notifications

import io.github.leonfox28.maxbo.core.paging.PagedController
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.CommentNotice
import io.github.leonfox28.maxbo.data.model.LikeNotice
import io.github.leonfox28.maxbo.data.model.NoticeKind
import io.github.leonfox28.maxbo.data.model.Paged
import io.github.leonfox28.maxbo.data.model.Status
import kotlinx.coroutines.CoroutineScope

/** [NoticeKind.Comments] or [NoticeKind.MentionComments]. */
class CommentNoticesController(
    private val api: WeiboApi,
    val kind: NoticeKind,
    scope: CoroutineScope,
) : PagedController<CommentNotice>(scope) {
    init {
        require(kind == NoticeKind.Comments || kind == NoticeKind.MentionComments)
    }

    override suspend fun fetchPage(cursor: String?): Paged<CommentNotice> =
        if (kind == NoticeKind.Comments) api.fetchCommentNotices(cursor)
        else api.fetchMentionComments(cursor)

    override fun idOf(item: CommentNotice) = item.id
}

class MentionStatusesController(private val api: WeiboApi, scope: CoroutineScope) :
    PagedController<Status>(scope) {
    override suspend fun fetchPage(cursor: String?) = api.fetchMentionStatuses(cursor)

    override fun idOf(item: Status) = item.id
}

class LikeNoticesController(private val api: WeiboApi, scope: CoroutineScope) :
    PagedController<LikeNotice>(scope) {
    override suspend fun fetchPage(cursor: String?) = api.fetchLikeNotices(cursor)

    override fun idOf(item: LikeNotice) = item.id
}

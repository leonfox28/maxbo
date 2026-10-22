package io.github.leonfox28.maxbo.ui.timeline

import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.core.paging.PagedController
import io.github.leonfox28.maxbo.core.paging.PagedState
import io.github.leonfox28.maxbo.core.session.SessionController
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.Paged
import io.github.leonfox28.maxbo.data.model.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The signed-in user's "最新微博" timeline, app-wide, so posts the user writes elsewhere can go in.
 *
 * Every request follows a user action: opening the tab, pulling to refresh, or scrolling to the
 * end. Nothing polls or prefetches (PLAN §2). Another account, or none, starts it over.
 */
class TimelineController(
    private val api: WeiboApi,
    private val session: SessionController,
    scope: CoroutineScope,
) : PagedController<Status>(scope) {
    private val uid: String?
        get() = (session.state.value as? SessionState.SignedIn)?.account?.uid

    init {
        scope.launch {
            session.state
                .map { (it as? SessionState.SignedIn)?.account?.uid }
                .distinctUntilChanged()
                .drop(1)
                .collect { uid ->
                    reset(if (uid == null) ListLoad.Ready(PagedState()) else ListLoad.Loading)
                }
        }
        if (uid == null) reset(ListLoad.Ready(PagedState()))
    }

    override suspend fun fetchPage(cursor: String?): Paged<Status> {
        val uid = uid ?: return Paged(emptyList())
        val page = api.fetchHomeTimeline(uid = uid, maxId = cursor)
        return Paged(page.statuses, nextCursor = page.nextCursor)
    }

    override fun idOf(item: Status) = item.id
}

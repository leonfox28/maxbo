package io.github.leonfox28.maxbo.ui.interactions

import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.ui.comments.CommentsController
import io.github.leonfox28.maxbo.ui.comments.RepostsController
import io.github.leonfox28.maxbo.ui.profile.UserStatusesController
import io.github.leonfox28.maxbo.ui.timeline.TimelineController

/**
 * The lists open on some page, by what they list, so a change the user makes shows in each of them,
 * as Riverpod's `ref.exists()` let the Flutter app find them. Pages register their lists while
 * open; only lists already open change: nothing loads for it (PLAN §2).
 *
 * Used on the main thread only.
 */
class OpenLists(private val timeline: TimelineController) {
    /** The posts of a user, by uid. */
    val userStatuses = Registry<String, UserStatusesController>()

    /** The reposts of a status, by its id. */
    val reposts = Registry<String, RepostsController>()

    /** The comments on a status, in any order, by its id and author's uid. */
    val comments = Registry<Pair<String, String>, CommentsController>()

    /** Puts [status], which the user just posted, atop the timeline and their own posts. */
    fun prependOwn(status: Status) {
        timeline.prepend(status)
        status.user?.id?.let { uid -> userStatuses.forEach(uid) { it.prepend(status) } }
    }

    /** Takes [status], which the user just deleted, out of the timeline and their own posts. */
    fun removeOwn(status: Status) {
        timeline.remove(status.id)
        status.user?.id?.let { uid -> userStatuses.forEach(uid) { it.remove(status.id) } }
    }

    /** Applies [edit] to each open list of the comments on status [statusId] by [authorId]. */
    fun editComments(statusId: String, authorId: String, edit: (CommentsController) -> Unit) =
        comments.forEach(statusId to authorId, edit)

    class Registry<K, L> {
        private val open = mutableMapOf<K, MutableList<L>>()

        /** Registers [list] under [key] until the returned handle is closed. */
        fun add(key: K, list: L): AutoCloseable {
            open.getOrPut(key) { mutableListOf() } += list
            return AutoCloseable {
                val lists = open[key] ?: return@AutoCloseable
                lists -= list
                if (lists.isEmpty()) open -= key
            }
        }

        fun forEach(key: K, action: (L) -> Unit) {
            open[key]?.toList()?.forEach(action)
        }
    }
}

package io.github.leonfox28.maxbo.ui.interactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.session.SessionController
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.showError
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

enum class LikeKind {
    Status,
    Comment,
}

/** A status or comment, which can be liked. */
data class LikeTarget(val kind: LikeKind, val id: String)

/** Whether something is liked by the user, and by how many. */
data class LikeState(val liked: Boolean, val count: Int) {
    val toggled: LikeState
        get() = LikeState(!liked, max(0, count + if (liked) -1 else 1))
}

/** The like of [Status] as loaded. */
val Status.like: LikeState
    get() = LikeState(liked, likesCount)

/**
 * A like the user changed in the app: [current], over what the loaded data said ([base]) when they
 * did. [pending] while Weibo has yet to confirm it.
 */
data class LikeChange(val base: LikeState, val current: LikeState, val pending: Boolean = false)

/**
 * The likes the user changed, shared by every list and page showing the same status or comment.
 * Another account has likes of its own, so signing in as one starts over.
 */
class LikesController(
    private val api: WeiboApi,
    session: SessionController,
    scope: CoroutineScope,
) {
    private val _changes = MutableStateFlow<Map<LikeTarget, LikeChange>>(emptyMap())
    val changes: StateFlow<Map<LikeTarget, LikeChange>> = _changes.asStateFlow()

    init {
        scope.launch { session.uid.drop(1).collect { _changes.value = emptyMap() } }
    }

    /**
     * Likes [target], or takes the like back, when the loaded data says it is [loaded].
     *
     * The change shows at once and is taken back if Weibo refuses it, with the [WeiboException]
     * rethrown. Does nothing while a change of [target] is on its way.
     */
    suspend fun toggle(target: LikeTarget, loaded: LikeState) {
        val previous = _changes.value[target]
        if (previous?.pending == true) return
        val next = resolve(previous, loaded).toggled
        set(target, LikeChange(loaded, next, pending = true))
        try {
            when (target.kind) {
                LikeKind.Status -> api.likeStatus(target.id, liked = next.liked)
                LikeKind.Comment -> api.likeComment(target.id, liked = next.liked)
            }
            set(target, LikeChange(loaded, next))
        } catch (e: WeiboException) {
            set(target, previous)
            throw e
        }
    }

    private fun set(target: LikeTarget, change: LikeChange?) {
        _changes.value =
            if (change == null) _changes.value - target else _changes.value + (target to change)
    }

    companion object {
        /**
         * What to show for something that the loaded data says is [loaded].
         *
         * A change applies only over the data it was made on: data loaded since already has it, and
         * is newer.
         */
        fun resolve(change: LikeChange?, loaded: LikeState): LikeState =
            if (change != null && change.base == loaded) change.current else loaded
    }
}

/** A like as the user last left it, and how to toggle it: null while a toggle is on its way. */
class ShownLike(val like: LikeState, val onToggle: (() -> Unit)?)

/**
 * The like of [target], which the loaded data says is [loaded], as the user last left it. A refused
 * toggle is explained in a snackbar.
 */
@Composable
fun rememberLike(target: LikeTarget, loaded: LikeState): ShownLike {
    val graph = LocalAppGraph.current
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val changes by graph.likes.changes.collectAsStateWithLifecycle()
    val change = changes[target]
    val onToggle: (() -> Unit)? =
        if (change?.pending == true) null
        else {
            {
                // App-wide: the like goes on, and is set right, whatever page is left.
                graph.appScope.launch {
                    try {
                        graph.likes.toggle(target, loaded)
                    } catch (e: WeiboException) {
                        snackbars.showError(e, navigator)
                    }
                }
            }
        }
    return ShownLike(LikesController.resolve(change, loaded), onToggle)
}

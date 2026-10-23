package io.github.leonfox28.maxbo.ui.notifications

import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.session.SessionController
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.core.util.devLog
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.NoticeKind
import io.github.leonfox28.maxbo.data.model.UnreadNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How soon after one check returning to the app or refreshing the home timeline checks again. */
const val UNREAD_RECHECK_MS = 60_000L

/**
 * Which kinds of notifications are unread, which the tab bar and the notification page's tabs mark.
 *
 * Checked only when the user signs in, opens the app or refreshes the home timeline ([check]),
 * never polled (PLAN §2). A kind stays marked until the user opens its tab ([markRead]); a check
 * only adds marks. Another account starts it over.
 *
 * @param clock milliseconds, on a clock that never goes back.
 */
class UnreadNoticesController(
    private val api: WeiboApi,
    private val session: SessionController,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val _state = MutableStateFlow(UnreadNotices())
    val state: StateFlow<UnreadNotices> = _state.asStateFlow()

    private var checkedAt: Long? = null
    @Volatile private var checking = false

    /** Kinds marked read while a check was out, whose result predates that. */
    private val readDuringCheck = mutableSetOf<NoticeKind>()

    /** Bumped for each account, so a check for the last one is dropped. */
    private var generation = 0

    init {
        scope.launch {
            session.state
                .map { (it as? SessionState.SignedIn)?.account?.uid }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    generation++
                    checkedAt = null
                    _state.value = UnreadNotices()
                }
        }
    }

    /**
     * Asks Weibo what is unread, unless it was asked less than [minIntervalMs] ago or is being
     * asked now. Failures leave things as they were: the mark is a hint only.
     *
     * @return whether it asked.
     */
    suspend fun check(minIntervalMs: Long = 0): Boolean {
        if (session.state.value !is SessionState.SignedIn || checking) return false
        val now = clock()
        checkedAt?.let { if (now - it < minIntervalMs) return false }
        checkedAt = now
        checking = true
        readDuringCheck.clear()
        val generation = generation
        try {
            val unread = api.fetchUnreadNotices()
            if (generation != this.generation) return true
            // Loading a list marks it read on Weibo's side, so a tab opened
            // since the check went out is up to date.
            _state.value = UnreadNotices(_state.value.kinds + unread.kinds - readDuringCheck)
        } catch (e: WeiboException) {
            devLog("unread notices: ${e.javaClass.simpleName}")
        } finally {
            checking = false
        }
        return true
    }

    /** [check] in the background, as for an event the user caused elsewhere. */
    fun requestCheck(minIntervalMs: Long = 0) {
        scope.launch { check(minIntervalMs) }
    }

    /** The user opened the tab of [kind]. */
    fun markRead(kind: NoticeKind) {
        if (checking) readDuringCheck += kind
        if (_state.value.has(kind)) _state.value = _state.value.without(kind)
    }
}

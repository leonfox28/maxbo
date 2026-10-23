package io.github.leonfox28.maxbo.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.session.SessionState

/**
 * Checks for unread notifications when the user signs in or opens the app, and when they come back
 * to it (at most once a minute), never in between (PLAN §2). For the app's root, outside the pages:
 * in one, the effects would run again each time it came back on top.
 */
@Composable
fun UnreadChecks() {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsStateWithLifecycle()
    val uid = (session as? SessionState.SignedIn)?.account?.uid
    LaunchedEffect(uid) { if (uid != null) graph.unread.check() }
    // Coming back to the app, not opening it: that is the check above.
    var away by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { away = true }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (away) graph.unread.requestCheck(UNREAD_RECHECK_MS)
        away = false
    }
}

package io.github.leonfox28.maxbo.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf

/** The stack of pages, [Route.Home] at the bottom. */
class Navigator {
    val backStack: SnapshotStateList<Route> = mutableStateListOf(Route.Home)

    fun push(route: Route) {
        backStack.add(route)
    }

    /** Closes the top page, unless it's the tabs. */
    fun pop() {
        if (backStack.size <= 1) return
        val closed = backStack.removeAt(backStack.lastIndex)
        (closed as? Route.Web)?.onClose?.invoke()
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator provided") }

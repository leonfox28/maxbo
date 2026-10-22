package io.github.leonfox28.maxbo.ui.navigation

/**
 * A page of the app. The tabs share one, [Home]; every other page goes on top of it. Like
 * go_router's `extra` in the Flutter app, a route may carry what the previous page already has,
 * such as the status that was tapped.
 */
sealed interface Route {
    /** The four tabs. */
    data object Home : Route

    data object Login : Route

    data object Settings : Route

    /** Debug builds only. */
    data object Diagnostics : Route
}

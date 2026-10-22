package io.github.leonfox28.maxbo.ui.navigation

import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.data.model.WeiboUser

/**
 * A page of the app. The tabs share one, [Home]; every other page goes on top of it. Like
 * go_router's `extra` in the Flutter app, a route may carry what the previous page already has,
 * such as the status that was tapped.
 */
sealed interface Route {
    /** The four tabs. */
    data object Home : Route

    data object Login : Route

    /** A status and its comments; [initial] shows until the full status loads. */
    data class StatusDetail(val id: String, val initial: Status? = null) : Route

    /** The pictures [pics], opened at [index]. */
    data class Viewer(val pics: List<PicInfo>, val index: Int) : Route

    /** The video of status [id], or its [index]th in a post mixing pictures and videos. */
    data class Video(val id: String, val index: Int? = null, val initial: Status? = null) : Route

    data class Profile(val uid: String, val initial: WeiboUser? = null) : Route

    /** A profile by screen name, as @mentions name users. */
    data class ProfileByName(val name: String) : Route

    data class SearchResults(val query: String) : Route

    /** A web page in the in-app browser; [onClose] runs once the user leaves it. */
    data class Web(val url: String, val onClose: (() -> Unit)? = null) : Route

    data object Settings : Route

    /** Debug builds only. */
    data object Diagnostics : Route
}

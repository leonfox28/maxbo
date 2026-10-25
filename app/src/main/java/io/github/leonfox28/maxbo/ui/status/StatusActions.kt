package io.github.leonfox28.maxbo.ui.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.ui.interactions.rememberStatusWrites
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.openExternally
import io.github.leonfox28.maxbo.ui.widget.status.StatusCardActions
import io.github.leonfox28.maxbo.ui.widget.text.StatusLink

/** What tapping parts of a status card (or a comment) does throughout the app. */
@Composable
fun rememberStatusCardActions(): StatusCardActions {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()
    val writes = rememberStatusWrites()
    return remember(navigator, context, writes) {
        fun openExternally(url: String) = context.openExternally(url, snackbars, scope)

        fun openStatus(status: Status) = navigator.push(Route.StatusDetail(status.id, status))

        fun viewPictures(pics: List<PicInfo>, index: Int, status: Status? = null) =
            navigator.push(Route.Viewer(pics, index, status?.id))

        StatusCardActions(
            onOpen = ::openStatus,
            onLink = { link ->
                when (link) {
                    is StatusLink.Web -> openExternally(link.url.toString())
                    // Mentions carry only the name.
                    is StatusLink.Mention -> navigator.push(Route.ProfileByName(link.screenName))
                    is StatusLink.Topic -> openSearchResults(navigator, "#${link.topic}#")
                    is StatusLink.Image ->
                        if (link.pics.isNotEmpty()) {
                            viewPictures(link.pics, 0)
                        } else {
                            openExternally(link.url.toString())
                        }
                }
            },
            onUser = { navigator.push(Route.Profile(it.id, it)) },
            // The detail page has the full text of long posts.
            onExpand = ::openStatus,
            onPicture = ::viewPictures,
            onVideo = { status, index -> navigator.push(Route.Video(status.id, index, status)) },
            onRepost = { writes.repost(it) },
            onDelete = { writes.delete(it) },
        )
    }
}

/** Opens the results of searching for [query]; `#话题#` searches the topic. */
fun openSearchResults(navigator: Navigator, query: String) {
    if (query.isNotBlank()) navigator.push(Route.SearchResults(query))
}

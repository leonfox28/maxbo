package io.github.leonfox28.maxbo.support

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.MaxboApp
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route

/**
 * The whole app, signed in as uid 7, talking to [web], as the Flutter tests' `signedInOverrides()`.
 */
class AppHarness(private val compose: ComposeContentTestRule, val web: FakeWeibo) {
    val graph: AppGraph = testGraph(restored = signedIn, web = web)
    val navigator = Navigator()

    /** Starts the app on [route] (or on the tabs), and waits for it to settle. */
    fun start(route: Route? = null) {
        route?.let(navigator::push)
        compose.setContent { MaxboApp(graph, navigator) }
        compose.waitForIdle()
    }

    fun text(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onNode(hasText(text, substring = substring), useUnmergedTree = true)

    fun shows(text: String, substring: Boolean = false) =
        compose
            .onAllNodes(hasText(text, substring = substring), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    fun count(text: String, substring: Boolean = false) =
        compose
            .onAllNodes(hasText(text, substring = substring), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size

    /** Waits for [text] to show, as for a reply from [web]. */
    fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(5_000) { shows(text, substring) }

    /** Paths of the requests made so far. */
    fun paths() = web.requests.map { it.url.encodedPath }

    /** The page on top. */
    val top: Route
        get() = navigator.backStack.last()
}

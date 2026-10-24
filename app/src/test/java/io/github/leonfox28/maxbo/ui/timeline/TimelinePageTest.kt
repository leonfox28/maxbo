package io.github.leonfox28.maxbo.ui.timeline

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.github.leonfox28.maxbo.MaxboApp
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.feedJson
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimelinePageTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()

    @After fun tearDown() = web.close()

    private fun status(n: Int) = compose.onNode(hasText("第${n}条"), useUnmergedTree = true)

    private fun shown(n: Int, text: String = "第${n}条") =
        compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun feeds() = web.requests.count { it.url.encodedPath == "/ajax/feed/friendstimeline" }

    @Test
    fun `a tap on the open tab scrolls back to the top, the next one refreshes`() {
        web.respond = {
            json(
                feedJson(
                        (1..30).map { statusJson(id = it.toLong(), text = "第${it}条") },
                        maxId = 100,
                    )
                    .toString()
            )
        }
        compose.setContent { MaxboApp(testGraph(restored = signedIn, web = web)) }
        compose.waitUntil(5_000) { shown(1) }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        status(1).assertDoesNotExist()

        compose.onNodeWithContentDescription("首页").performClick()
        compose.waitUntil(5_000) { shown(1) }
        assertEquals(1, feeds())

        compose.onNodeWithContentDescription("首页").performClick()
        compose.waitUntil(5_000) { feeds() == 2 }
    }

    @Test
    fun `a refresh from the top shows the newest status, not the old first one`() {
        var newest = 30
        web.respond = {
            json(
                feedJson((newest downTo 1).map { statusJson(id = it.toLong(), text = "第${it}条") })
                    .toString()
            )
        }
        compose.setContent { MaxboApp(testGraph(restored = signedIn, web = web)) }
        compose.waitUntil(5_000) { shown(30) }

        newest = 32
        compose.onNodeWithContentDescription("首页").performClick()
        compose.waitUntil(5_000) { feeds() == 2 }
        compose.waitUntil(5_000) { shown(32) }
    }

    @Test
    fun `pulling to refresh checks notifications, once a minute`() {
        web.respond = {
            if (it.url.encodedPath == "/2/remind/push_count.json") json("""{"cmt":1}""")
            else json(feedJson(listOf(statusJson(text = "首页的一条微博"))).toString())
        }
        val graph = testGraph(restored = signedIn, web = web)
        compose.setContent { MaxboApp(graph) }
        compose.waitUntil(5_000) { shown(0, "首页的一条微博") }
        fun checks() = web.requests.count { it.url.encodedPath == "/2/remind/push_count.json" }
        // Signing in (or opening the app) checks once.
        compose.waitUntil(5_000) { checks() == 1 }

        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { feeds() == 2 }
        // Within a minute of the last one.
        assertEquals(1, checks())
        assertTrue(graph.unread.state.value.any)
    }
}

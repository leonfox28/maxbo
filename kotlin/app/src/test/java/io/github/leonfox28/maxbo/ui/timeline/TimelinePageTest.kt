package io.github.leonfox28.maxbo.ui.timeline

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import io.github.leonfox28.maxbo.MaxboApp
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.feedJson
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun shown(n: Int) =
        compose
            .onAllNodes(hasText("第${n}条"), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

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
}

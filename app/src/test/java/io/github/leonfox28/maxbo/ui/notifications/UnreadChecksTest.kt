package io.github.leonfox28.maxbo.ui.notifications

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import io.github.leonfox28.maxbo.MaxboApp
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.feedJson
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnreadChecksTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val web = FakeWeibo()
    @Volatile private var now = 0L
    private val graph = testGraph(restored = signedIn, web = web, clock = { now })

    init {
        web.respond = {
            if (it.url.encodedPath == "/2/remind/push_count.json") json("""{"cmt":1}""")
            else json(feedJson(listOf(statusJson(text = "首页的一条微博"))).toString())
        }
    }

    @Before
    fun setUp() {
        compose.setContent { MaxboApp(graph) }
        // Opening the app checks once.
        compose.waitUntil(5_000) { graph.unread.state.value.any }
        assertEquals(1, checks())
    }

    @After fun tearDown() = web.close()

    private fun checks() = web.requests.count { it.url.encodedPath == "/2/remind/push_count.json" }

    /** Whether nothing has checked within the last minute, so this one asks. */
    private fun quietForAMinute() = runBlocking { graph.unread.check(UNREAD_RECHECK_MS) }

    private fun leaveAndComeBack() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    @Test
    fun `coming back to the app checks again, but not within a minute`() {
        leaveAndComeBack()
        assertEquals(1, checks())

        now += UNREAD_RECHECK_MS
        leaveAndComeBack()
        compose.waitUntil(5_000) { checks() == 2 }
    }

    @Test
    fun `closing a page over the tabs is not coming back to the app`() {
        now += UNREAD_RECHECK_MS
        compose.onNodeWithContentDescription("我").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        assertTrue(quietForAMinute())
    }
}

package io.github.leonfox28.maxbo.ui.search

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import io.github.leonfox28.maxbo.support.AppHarness
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.hotItemJson
import io.github.leonfox28.maxbo.support.hotSearchJson
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.ui.navigation.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SearchPageTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val app = AppHarness(compose, web)
    private val list =
        hotSearchJson(
            hotgovs = listOf(obj("word" to "#置顶的话题#", "icon_desc" to "热")),
            realtime =
                listOf(
                    hotItemJson("第一条", realpos = 1, icon = "热", num = 1587784),
                    hotItemJson("付费的一条", icon = "首发", smallIcon = "商", isAd = 1),
                    hotItemJson("第二条", realpos = 2, scheme = "第二条", num = 0),
                ),
        )

    @After fun tearDown() = web.close()

    private fun open() {
        app.start()
        compose.onNodeWithContentDescription("搜索").performClick()
    }

    private fun hotRequests() = app.paths().count { it == "/ajax/side/hotSearch" }

    private val field
        get() = compose.onNode(hasSetTextAction())

    @Test
    fun `lists the pinned entries, then the ranking as sent`() {
        web.respond = { json(list.toString()) }
        open()
        app.waitFor("第一条")

        assertEquals(1, hotRequests())
        val tops =
            listOf("置顶的话题", "第一条", "付费的一条", "第二条").map {
                app.text(it).fetchSemanticsNode().boundsInRoot.top
            }
        assertEquals(tops.sorted(), tops)
        app.text("1").assertExists()
        app.text("2").assertExists()
        app.text("158万").assertExists()
        // Paid entries keep Weibo's 商 label.
        app.text("商").assertExists()
        app.text("首发").assertDoesNotExist()
    }

    @Test
    fun `an entry opens the posts found for it`() {
        web.respond = { json(list.toString()) }
        open()
        app.waitFor("第一条")

        app.text("第一条").performClick()
        // A topic, as Weibo sends it.
        assertEquals(Route.SearchResults("#第一条#"), app.top)

        app.navigator.pop()
        compose.waitForIdle()
        app.text("第二条").performClick()
        assertEquals(Route.SearchResults("第二条"), app.top)
    }

    @Test
    fun `searches for what the user types`() {
        web.respond = { json(list.toString()) }
        open()
        app.waitFor("第一条")

        field.performTextReplacement("  某个词 ")
        field.performImeAction()
        assertEquals(Route.SearchResults("某个词"), app.top)

        // What was typed is still there to change.
        app.navigator.pop()
        compose.waitForIdle()
        app.text("  某个词 ").assertExists()
        field.performTextReplacement("#某个话题#")
        field.performImeAction()
        assertEquals(Route.SearchResults("#某个话题#"), app.top)
    }

    @Test
    fun `a blank search does nothing - the text can be cleared`() {
        web.respond = { json(list.toString()) }
        open()
        app.waitFor("第一条")
        compose.onNodeWithContentDescription("清除").assertDoesNotExist()

        field.performTextReplacement("   ")
        field.performImeAction()
        assertEquals(Route.Home, app.top)

        compose.onNodeWithContentDescription("清除").performClick()
        compose.onNodeWithContentDescription("清除").assertDoesNotExist()
        app.text("搜索微博").assertExists()
    }

    @Test
    fun `tapping the tab while at the top refreshes`() {
        web.respond = { json(list.toString()) }
        open()
        app.waitFor("第一条")
        assertEquals(1, hotRequests())

        compose.onNodeWithContentDescription("搜索").performClick()
        compose.waitUntil(5_000) { hotRequests() == 2 }
        app.waitFor("第一条")
    }

    @Test
    fun `a failed load can be retried`() {
        web.respond = { json("""{"ok":0,"message":"服务繁忙"}""", status = 500) }
        open()
        app.waitFor("服务繁忙")
        // Searching doesn't need the list.
        app.text("搜索微博").assertExists()

        web.respond = { json(list.toString()) }
        app.text("重试").performClick()
        app.waitFor("第一条")
    }
}

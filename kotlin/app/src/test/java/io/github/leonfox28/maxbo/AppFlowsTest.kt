package io.github.leonfox28.maxbo

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.leonfox28.maxbo.support.AppHarness
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.feedJson
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.plus
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.userJson
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Flows across pages of the whole app, as the Flutter app_test had them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-mdpi")
class AppFlowsTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val app = AppHarness(compose, web)

    init {
        web.respond = { request ->
            when (request.url.encodedPath) {
                "/ajax/feed/friendstimeline" ->
                    json(
                        feedJson(
                                listOf(
                                    statusJson(id = 1, text = "别人的微博"),
                                    statusJson(id = 2, text = "我的微博", user = userJson(id = 7)),
                                ) + (3..20L).map { statusJson(id = it, text = "第 $it 条") }
                            )
                            .toString()
                    )
                "/ajax/statuses/destroy" -> json((obj("ok" to 1) + statusJson(id = 2)).toString())
                else -> MockResponse(code = 404)
            }
        }
    }

    @After fun tearDown() = web.close()

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun tab(label: String) = compose.onNodeWithContentDescription(label)

    @Test
    fun `the user deletes their own post from the home tab`() {
        app.start()
        app.waitFor("我的微博")

        compose.onNodeWithContentDescription("更多").performClick()
        compose.waitForIdle()
        tap("删除")
        app.waitFor("确定删除这条微博吗？")
        tap("删除")
        compose.waitUntil(5_000) { !app.shows("我的微博") }

        assertEquals(1, app.paths().count { it == "/ajax/statuses/destroy" })
        assertFalse(app.shows("我的微博"))
        app.text("别人的微博").assertExists()
    }

    @Test
    fun `settings open from Me, and the about page and licences from there`() {
        app.start()

        tab("我").performClick()
        compose.waitForIdle()
        tap("设置")
        tap("关于 maxbo")
        app.text("maxbo 是非官方客户端", substring = true).assertExists()

        tap("开源许可")
        app.waitFor("以 GNU GPL-3.0 许可发布")
        // The libraries, as the plugin listed them at build time, with their
        // licences.
        app.waitFor("Apache License 2.0", substring = true)
    }

    /** The tabs of the pill, all together. */
    private fun pill(): Rect =
        listOf("首页", "搜索", "通知", "我")
            .map {
                compose
                    .onNode(hasContentDescription(it, substring = true))
                    .fetchSemanticsNode()
                    .boundsInRoot
            }
            .reduce { a, b ->
                Rect(a.left, a.top, maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
            }

    @Test
    fun `the pill sits in the middle of a phone`() {
        app.start()

        assertEquals(411f / 2, pill().center.x, 1f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `a wide window has the pill in its corner, and wide cards`() {
        app.start()
        app.waitFor("别人的微博")

        // The tabs, 8 in from the pill's edge, which is 16 in from the window's.
        assertEquals(1280f - 16 - 8, pill().right, 1f)
        val text = app.text("别人的微博").fetchSemanticsNode().boundsInRoot
        // In a timeline 1000 wide, centred.
        assertEquals((1280f - 1000) / 2 + 16, text.left, 1f)

        // In the corner it still switches tabs.
        tab("搜索").performClick()
        compose.waitForIdle()
        assertFalse(app.shows("别人的微博"))
    }
}

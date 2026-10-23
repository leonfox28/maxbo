package io.github.leonfox28.maxbo.ui.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import io.github.leonfox28.maxbo.support.AppHarness
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.searchJson
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.topicHeadsJson
import io.github.leonfox28.maxbo.ui.navigation.Route
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The app bar hiding on scroll is BarsOnScroll's, tested with the timeline.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SearchResultsPageTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val app = AppHarness(compose, web)

    @After fun tearDown() = web.close()

    private fun searches() =
        web.requests.filter { it.url.encodedPath == "/ajax/statuses/search" }.map { it.url }

    @Test
    fun `a topic - its lead and counts head the posts in it`() {
        web.respond = {
            json(
                searchJson(
                        listOf(
                            statusJson(id = 1, text = "第一条结果"),
                            statusJson(id = 2, text = "第二条结果"),
                        ),
                        topicHeads = topicHeadsJson(name = "某个话题", summary = "话题导语"),
                    )
                    .toString()
            )
        }
        app.start(Route.SearchResults("#某个话题#"))
        app.waitFor("第一条结果")

        assertEquals("#某个话题#", searches().single().queryParameter("q"))
        // In the app bar and atop the list.
        assertEquals(2, app.count("#某个话题#"))
        app.text("4226万阅读 · 9365讨论 · 1997原创人数").assertExists()
        app.text("话题导语", substring = true).assertExists()
    }

    @Test
    fun `a word - just the posts, a page at a time`() {
        web.respond = {
            json(
                when (it.url.queryParameter("page")) {
                    "1" -> searchJson(listOf(statusJson(id = 1, text = "第一页的结果")))
                    "2" -> searchJson(listOf(statusJson(id = 2, text = "第二页的结果")))
                    else -> searchJson(emptyList())
                }.toString()
            )
        }
        app.start(Route.SearchResults("某个词"))
        app.waitFor("第一页的结果")

        app.text("导语", substring = true).assertDoesNotExist()
        app.text("阅读", substring = true).assertDoesNotExist()

        // Too short to scroll: the footer offers more.
        app.text("加载更多").performClick()
        app.waitFor("第二页的结果")
        app.text("加载更多").performClick()
        app.waitFor("没有更多了")
        assertEquals(listOf("1", "2", "3"), searches().map { it.queryParameter("page") })
    }

    @Test
    fun `nothing found says so`() {
        web.respond = { json(searchJson(emptyList(), total = 0).toString()) }
        app.start(Route.SearchResults("没有结果的词"))

        app.waitFor("没有找到相关微博")
    }

    @Test
    fun `a failed search can be retried`() {
        web.respond = { json("""{"ok":0,"message":"服务繁忙"}""", status = 500) }
        app.start(Route.SearchResults("某个词"))
        app.waitFor("服务繁忙")

        web.respond = { json(searchJson(listOf(statusJson(text = "找到的微博"))).toString()) }
        app.text("重试").performClick()
        app.waitFor("找到的微博")
    }

    @Test
    fun `the topic searched for does not open again - others do`() {
        web.respond = {
            json(
                searchJson(
                        listOf(
                            statusJson(
                                text =
                                    "<a href=\"//s.weibo.com/weibo?q=%23某个话题%23\">#某个话题#</a>和" +
                                        "<a href=\"//s.weibo.com/weibo?q=%23别的话题%23\">#别的话题#</a>"
                            )
                        ),
                        topicHeads = topicHeadsJson(name = "某个话题"),
                    )
                    .toString()
            )
        }
        app.start(Route.SearchResults("#某个话题#"))
        app.waitFor("#某个话题#和#别的话题#")

        val body = app.text("#某个话题#和#别的话题#")
        body.performFirstLinkClick { it.start == 0 }
        assertEquals(Route.SearchResults("#某个话题#"), app.top)
        assertEquals(2, app.navigator.backStack.size)

        body.performFirstLinkClick { it.start > 0 }
        assertEquals(Route.SearchResults("#别的话题#"), app.top)
    }

    @Test
    fun `Weibo's own results are a tap away`() {
        web.respond = { json(searchJson(listOf(statusJson())).toString()) }
        app.start(Route.SearchResults("#某个话题#"))

        compose.onNodeWithContentDescription("网页版搜索结果").performClick()

        val url = (app.top as Route.Web).url.toHttpUrl()
        assertEquals("m.weibo.cn", url.host)
        assertEquals("231522", url.queryParameter("containerid"))
        assertEquals("#某个话题#", url.queryParameter("q"))
        assertEquals(setOf("containerid", "q"), url.queryParameterNames)
    }
}

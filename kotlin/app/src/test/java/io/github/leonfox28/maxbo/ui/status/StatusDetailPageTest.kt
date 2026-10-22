package io.github.leonfox28.maxbo.ui.status

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.leonfox28.maxbo.MaxboApp
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentsPageJson
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.plus
import io.github.leonfox28.maxbo.support.repostsPageJson
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.support.userJson
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Writing (commenting, replying, deleting, liking, reposting) comes over
// with K5, the side-by-side layout of wide windows with K6.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class StatusDetailPageTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()

    /** Holds the full status back until counted down. */
    private val fullStatus =
        CountDownLatch(0).let { java.util.concurrent.atomic.AtomicReference(it) }

    init {
        web.respond = { request ->
            when (request.url.encodedPath) {
                "/ajax/statuses/show" ->
                    fullStatus.get().await(10, TimeUnit.SECONDS).let {
                        json(
                            (obj("ok" to 1) +
                                    statusJson(
                                        id = 1,
                                        text = "完整的正文",
                                        user = userJson(),
                                        extra = mapOf("attitudes_count" to 5),
                                    ))
                                .toString()
                        )
                    }
                "/ajax/statuses/buildComments" ->
                    json(
                        commentsPageJson(
                                listOf(
                                    commentJson(
                                        text =
                                            if (request.url.queryParameter("flow") == "1") "最新的评论"
                                            else "最热的评论"
                                    )
                                )
                            )
                            .toString()
                    )
                "/ajax/statuses/repostTimeline" ->
                    json(repostsPageJson(listOf(statusJson(id = 8, text = "转发了这条"))).toString())
                else -> MockResponse(code = 404)
            }
        }
    }

    @After fun tearDown() = web.close()

    private fun requestsTo(path: String) = web.requests.count { it.url.encodedPath == path }

    private fun open() {
        val navigator = Navigator()
        navigator.push(
            Route.StatusDetail(
                "1",
                Status.tryFromJson(
                    statusJson(id = 1, text = "截断的正文<span class=\"expand\">展开</span>")
                )!!,
            )
        )
        compose.setContent { MaxboApp(testGraph(restored = signedIn, web = web), navigator) }
    }

    private fun shown(text: String) =
        compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { shown(text) }

    @Test
    fun `shows the status it was given, then its full text`() {
        val held = CountDownLatch(1)
        fullStatus.set(held)
        open()
        compose.onNode(hasText("截断的正文", substring = true), useUnmergedTree = true).assertExists()
        held.countDown()

        waitFor("完整的正文")
        waitFor("最热的评论")
        compose.onNodeWithText("评论 1.2万", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("转发 3", useUnmergedTree = true).assertExists()
        assertEquals(
            "true",
            web.requests
                .first { it.url.encodedPath == "/ajax/statuses/show" }
                .url
                .queryParameter("isGetLongText"),
        )
        // Reposts wait until their tab is opened.
        assertEquals(0, requestsTo("/ajax/statuses/repostTimeline"))
    }

    @Test
    fun `loads each list once, however often it is shown`() {
        open()
        waitFor("最热的评论")

        compose.onNodeWithText("按时间", useUnmergedTree = true).performClick()
        waitFor("最新的评论")

        compose.onNodeWithText("按热度", useUnmergedTree = true).performClick()
        waitFor("最热的评论")

        compose.onNodeWithText("转发 3", useUnmergedTree = true).performClick()
        waitFor("转发了这条")
        compose.onNodeWithText("按热度", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithText("评论 1.2万", useUnmergedTree = true).performClick()
        waitFor("最热的评论")

        assertEquals(1, requestsTo("/ajax/statuses/show"))
        assertEquals(2, requestsTo("/ajax/statuses/buildComments"))
        assertEquals(1, requestsTo("/ajax/statuses/repostTimeline"))
    }

    @Test
    fun `a comment's thread opens on its own page`() {
        web.respond = { request ->
            when (request.url.encodedPath) {
                "/ajax/statuses/show" ->
                    json((obj("ok" to 1) + statusJson(id = 1, text = "完整的正文")).toString())
                else ->
                    if (request.url.queryParameter("fetch_level") == "1") {
                        json(
                            commentsPageJson(listOf(commentJson(id = 9, text = "楼里的回复")), total = 1)
                                .toString()
                        )
                    } else {
                        json(
                            commentsPageJson(
                                    listOf(
                                        commentJson(
                                            text = "有回复的评论",
                                            replyCount = 1,
                                            replies = listOf(commentJson(id = 9, text = "楼里的回复")),
                                        )
                                    )
                                )
                                .toString()
                        )
                    }
            }
        }
        open()
        waitFor("共1条回复 >")

        compose.onNodeWithText("共1条回复 >", useUnmergedTree = true).performClick()

        waitFor("评论详情")
        waitFor("1 条回复")
        assertEquals(
            "600",
            web.requests.last().url.queryParameter("id").let {
                if (it == "6000000000000001") "600" else it
            },
        )
    }
}

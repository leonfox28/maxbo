package io.github.leonfox28.maxbo.ui.notifications

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.leonfox28.maxbo.data.model.NoticeKind
import io.github.leonfox28.maxbo.support.AppHarness
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentNoticeJson
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.likeNoticeJson
import io.github.leonfox28.maxbo.support.noticePageJson
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.ui.navigation.Route
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class NotificationsPageTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val app = AppHarness(compose, web)

    /** What push_count answers. */
    @Volatile private var unreadCounts = "{}"

    /** The comment the comments list has, and how long Weibo takes to give it. */
    @Volatile private var newestComment = "收到的评论"
    @Volatile private var commentsDelay = 0L

    init {
        web.respond = {
            when (it.url.encodedPath) {
                "/2/remind/push_count.json" -> json(unreadCounts)
                "/ajax/message/cmt" -> {
                    Thread.sleep(commentsDelay)
                    json(
                        noticePageJson(
                                "comments",
                                listOf(
                                    commentNoticeJson(
                                        id = 11,
                                        text = newestComment,
                                        status = statusJson(id = 101, text = "我发的那条"),
                                        replyTo = commentJson(id = 10, text = "我之前的评论"),
                                    )
                                ),
                            )
                            .toString()
                    )
                }
                "/ajax/comments/mentions" ->
                    json(
                        noticePageJson(
                                "comments",
                                listOf(commentNoticeJson(id = 12, text = "提到我的评论")),
                            )
                            .toString()
                    )
                "/ajax/statuses/mentions" ->
                    json(
                        noticePageJson("statuses", listOf(statusJson(id = 102, text = "提到我的微博")))
                            .toString()
                    )
                "/ajax/message/attitudes" ->
                    json(
                        noticePageJson(
                                "attitudes",
                                listOf(
                                    likeNoticeJson(
                                        id = 13,
                                        status = statusJson(id = 103, text = "被赞的微博"),
                                        cut = true,
                                    )
                                ),
                            )
                            .toString()
                    )
                else -> MockResponse(code = 404)
            }
        }
    }

    @After fun tearDown() = web.close()

    private fun open() {
        app.start()
        compose.onNodeWithContentDescription("通知", substring = true).performClick()
        app.waitFor("收到的评论")
        // Past the check made as the app opened, so it can't read the counts a test sets next.
        checkUnread()
    }

    private fun tab(label: String) = compose.onNodeWithContentDescription(label)

    private fun loads(path: String) = app.paths().count { it == path }

    /**
     * Checks for unread notices, once the check made as the app opened is done: a check while one
     * is on its way does nothing, and a tab opened meanwhile counts as read for that one.
     */
    private fun checkUnread() {
        compose.waitUntil(5_000) { runBlocking { app.graph.unread.check() } }
        compose.waitForIdle()
    }

    @Test
    fun `loads a tab only when it is opened`() {
        open()
        val lists =
            setOf(
                "/ajax/message/cmt",
                "/ajax/comments/mentions",
                "/ajax/statuses/mentions",
                "/ajax/message/attitudes",
            )
        assertEquals(listOf("/ajax/message/cmt"), app.paths().filter { it in lists })
        app.text("我之前的评论", substring = true).assertExists()
        app.text("我发的那条", substring = true).assertExists()

        tab("@我的微博").performClick()
        app.waitFor("提到我的微博")
        assertEquals("/ajax/statuses/mentions", app.paths().last())

        tab("赞").performClick()
        app.waitFor("点赞的人 赞了你的微博")
        assertEquals("/ajax/message/attitudes", app.paths().last())

        // Lists stay loaded when the user comes back to them.
        tab("评论").performClick()
        app.waitFor("收到的评论")
        assertEquals(1, loads("/ajax/message/cmt"))
    }

    @Test
    fun `the open tab shows what is new, however long Weibo takes`() {
        open()
        newestComment = "新收到的评论"
        commentsDelay = 500
        unreadCounts = """{"cmt":1}"""
        checkUnread()

        app.waitFor("新收到的评论")
    }

    @Test
    fun `a dot marks unread tabs until they are opened`() {
        open()
        unreadCounts = """{"cmt":1,"attitude":2}"""
        checkUnread()

        // The comments tab is open: it reloads, and is read.
        compose.waitUntil(5_000) { loads("/ajax/message/cmt") == 2 }
        assertEquals(setOf(NoticeKind.Likes), app.graph.unread.state.value.kinds)
        tab("评论，有未读").assertDoesNotExist()
        tab("赞，有未读").assertExists()
        // The tab bar's bell is red too.
        compose.onNodeWithContentDescription("通知，有未读").assertExists()

        tab("赞，有未读").performClick()
        app.waitFor("点赞的人 赞了你的微博")
        assertFalse(app.graph.unread.state.value.any)
        tab("赞，有未读").assertDoesNotExist()
    }

    @Test
    fun `a loaded list keeps its dot until its tab is opened`() {
        open()
        tab("赞").performClick()
        app.waitFor("点赞的人 赞了你的微博")
        tab("评论").performClick()
        compose.waitForIdle()
        assertEquals(1, loads("/ajax/message/attitudes"))

        unreadCounts = """{"attitude":1}"""
        checkUnread()
        assertEquals(1, loads("/ajax/message/attitudes"))
        tab("赞，有未读").assertExists()

        // Read elsewhere, say: the dot still waits for the user.
        unreadCounts = "{}"
        checkUnread()
        tab("赞，有未读").assertExists()

        tab("赞，有未读").performClick()
        compose.waitUntil(5_000) { loads("/ajax/message/attitudes") == 2 }
        assertFalse(app.graph.unread.state.value.any)
    }

    @Test
    fun `notices open the post they are about`() {
        open()
        app.text("收到的评论").performClick()
        assertEquals("101", (app.top as Route.StatusDetail).id)

        app.navigator.pop()
        compose.waitForIdle()
        tab("赞").performClick()
        app.waitFor("被赞的微博", substring = true)
        // A cut-down post opens by id alone.
        app.text("被赞的微博", substring = true).performClick()
        assertEquals(Route.StatusDetail("103"), app.top)
    }
}

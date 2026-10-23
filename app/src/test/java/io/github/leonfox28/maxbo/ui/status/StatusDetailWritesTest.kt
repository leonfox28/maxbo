package io.github.leonfox28.maxbo.ui.status

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.github.leonfox28.maxbo.MaxboApp
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.closeSheet
import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentsPageJson
import io.github.leonfox28.maxbo.support.form
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.plus
import io.github.leonfox28.maxbo.support.repostsPageJson
import io.github.leonfox28.maxbo.support.showsTextField
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.support.userJson
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// "Pressing the bar's buttons shows no ink" (Flutter) has no counterpart:
// the bar turns ripples off with LocalRippleConfiguration.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class StatusDetailWritesTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val navigator = Navigator()

    /** Answers to writes, by path; a test sets what it needs. */
    private val writes = ConcurrentHashMap<String, () -> MockResponse>()
    @Volatile private var commentsCount = 12345
    @Volatile private var author: JsonObject = userJson()

    init {
        web.respond = { request ->
            val path = request.url.encodedPath
            when {
                path == "/ajax/statuses/show" ->
                    json(
                        (obj("ok" to 1) +
                                statusJson(
                                    id = 1,
                                    text = "完整的正文",
                                    user = author,
                                    extra =
                                        mapOf(
                                            "attitudes_count" to 5,
                                            "comments_count" to commentsCount,
                                        ),
                                ))
                            .toString()
                    )
                path == "/ajax/statuses/buildComments" ->
                    json(
                        commentsPageJson(
                                listOf(
                                    commentJson(
                                        text =
                                            if (request.url.queryParameter("flow") == "1") "最新的评论"
                                            else "最热的评论"
                                    ),
                                    commentJson(
                                        id = 6000000000000002,
                                        text = "我写的评论",
                                        user = userJson(id = 7, name = "我"),
                                    ),
                                )
                            )
                            .toString()
                    )
                writes.containsKey(path) -> writes.getValue(path)()
                path == "/ajax/statuses/repostTimeline" ->
                    json(repostsPageJson(listOf(statusJson(id = 8, text = "转发了这条"))).toString())
                else -> MockResponse(code = 404)
            }
        }
    }

    @After fun tearDown() = web.close()

    /**
     * The latest request, but for the unread check made as the app opened, which may come any time.
     */
    private fun lastRequest() =
        web.requests.last { it.url.encodedPath != "/2/remind/push_count.json" }

    private fun sentForm() = lastRequest().form

    private fun requestsTo(path: String) = web.requests.count { it.url.encodedPath == path }

    private fun open(comments: Int = 12345, user: JsonObject = userJson()) {
        commentsCount = comments
        author = user
        navigator.push(
            Route.StatusDetail(
                "1",
                Status.tryFromJson(
                    statusJson(id = 1, text = "截断的正文<span class=\"expand\">展开</span>", user = user)
                )!!,
            )
        )
        compose.setContent { MaxboApp(testGraph(restored = signedIn, web = web), navigator) }
        waitFor("完整的正文")
        waitFor("最热的评论")
    }

    private fun shows(text: String) =
        compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { shows(text) }

    private fun text(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)

    private fun top(text: String) = text(text).fetchSemanticsNode().boundsInRoot.top

    /** Writes [text] in the composer sheet and sends it. */
    private fun send(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("发送").performClick()
    }

    @Test
    fun `a comment is sent from the bar and goes atop the list`() {
        open()
        writes["/ajax/comments/create"] = {
            json(
                obj(
                        "ok" to 1,
                        "msg" to "评论成功",
                        "data" to commentJson(id = 9, text = "新评论", user = userJson(id = 7)),
                    )
                    .toString()
            )
        }

        compose.onNodeWithText("写评论…").performClick()
        compose.onNodeWithText("发送").assertIsNotEnabled()
        send("  新评论 ")
        waitFor("评论成功")

        assertEquals("/ajax/comments/create", lastRequest().url.encodedPath)
        assertEquals("1", sentForm()["id"])
        assertEquals("新评论", sentForm()["comment"])
        assertFalse(compose.showsTextField())
        text("评论 1.2万").assertExists()
        assertTrue(top("新评论") < top("最热的评论"))
    }

    @Test
    fun `a failed comment stays in the sheet with the reason`() {
        open()
        writes["/ajax/comments/create"] = { json("""{"ok":0,"msg":"评论太频繁了"}""") }

        compose.onNodeWithText("写评论…").performClick()
        send("新评论")
        waitFor("评论太频繁了")
        text("新评论").assertExists()

        // Closing the sheet keeps the draft for next time.
        compose.closeSheet()
        assertFalse(compose.showsTextField())
        compose.onNodeWithText("写评论…").performClick()
        compose.waitForIdle()
        text("新评论").assertExists()
    }

    @Test
    fun `tapping a comment replies to it`() {
        open()
        writes["/ajax/comments/reply"] = {
            json(obj("ok" to 1, "data" to commentJson(id = 10, text = "回复内容")).toString())
        }

        text("最热的评论").performClick()
        waitFor("回复 @评论者")
        send("回复内容")
        // The comment's thread preview now has the reply.
        waitFor("共1条回复 >")

        assertEquals("/ajax/comments/reply", lastRequest().url.encodedPath)
        assertEquals("1", sentForm()["id"])
        assertEquals("6000000000000001", sentForm()["cid"])
    }

    @Test
    fun `the user can delete their own comments only`() {
        open()
        writes["/ajax/statuses/destroyComment"] = {
            json(obj("ok" to 1, "data" to commentJson()).toString())
        }

        // Others' comments only offer a reply.
        text("最热的评论").performTouchInput { longClick() }
        waitFor("回复 @评论者")
        assertFalse(shows("删除"))
        compose.closeSheet()

        text("我写的评论").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("删除").performClick()
        waitFor("确定删除这条评论吗？")
        compose.onNodeWithText("删除").performClick()
        waitFor("已删除")

        assertEquals("/ajax/statuses/destroyComment", lastRequest().url.encodedPath)
        assertEquals(mapOf("cid" to "6000000000000002"), sentForm())
        assertFalse(shows("我写的评论"))
    }

    @Test
    fun `others' statuses offer no menu`() {
        open()

        compose.onNodeWithContentDescription("更多").assertDoesNotExist()
    }

    @Test
    fun `deleting their own status leaves its page`() {
        open(user = userJson(id = 7, name = "我"))
        writes["/ajax/statuses/destroy"] = {
            json(obj("ok" to 1, "data" to statusJson(id = 1)).toString())
        }

        compose.onNodeWithContentDescription("更多").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除").performClick()
        waitFor("确定删除这条微博吗？")
        compose.onNodeWithText("删除").performClick()
        waitFor("已删除")

        // The unread check made as the app opened may come after it.
        val destroy = web.requests.single { it.url.encodedPath == "/ajax/statuses/destroy" }
        assertEquals(mapOf("id" to "1"), destroy.form)
        assertEquals(Route.Home, navigator.backStack.last())
    }

    @Test
    fun `the bar likes the status, and the tab count follows`() {
        open()
        writes["/ajax/statuses/setLike"] = { json("""{"ok":1}""") }
        text("赞 5").assertExists()

        text("5").performClick()
        waitFor("赞 6")
        // The like shows before it is sent.
        compose.waitUntil(5_000) { requestsTo("/ajax/statuses/setLike") == 1 }

        assertEquals("/ajax/statuses/setLike", lastRequest().url.encodedPath)
        assertEquals(mapOf("id" to "1"), sentForm())
    }

    @Test
    fun `the bar reposts the status, which goes atop the open list and is counted`() {
        open()
        writes["/ajax/statuses/normal_repost"] = {
            json(
                obj(
                        "ok" to 1,
                        "data" to
                            obj(
                                "statuses" to
                                    statusJson(
                                        id = 9,
                                        text = "我的转发",
                                        user = userJson(id = 7, name = "我"),
                                        retweeted = statusJson(id = 1),
                                    )
                            ),
                    )
                    .toString()
            )
        }
        text("转发 3").performClick()
        waitFor("转发了这条")

        compose.onNode(hasText("转发") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { compose.showsTextField() }
        // The sheet's, over the bar's.
        compose.onAllNodes(hasText("转发") and hasClickAction()).onLast().performClick()
        waitFor("转发成功")

        assertEquals("/ajax/statuses/normal_repost", lastRequest().url.encodedPath)
        assertEquals("1", sentForm()["id"])
        waitFor("转发 4")
        assertTrue(top("我的转发") < top("转发了这条"))
    }

    @Test
    fun `a comment is counted until data that has it loads`() {
        open(comments = 3)
        writes["/ajax/comments/create"] = {
            json(obj("ok" to 1, "data" to commentJson(id = 9, text = "新评论")).toString())
        }

        compose.onNodeWithText("写评论…").performClick()
        send("新评论")
        waitFor("评论 4")

        // Weibo now counts it too.
        commentsCount = 4
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { requestsTo("/ajax/statuses/show") == 2 }
        compose.waitForIdle()

        text("评论 4").assertExists()
    }
}

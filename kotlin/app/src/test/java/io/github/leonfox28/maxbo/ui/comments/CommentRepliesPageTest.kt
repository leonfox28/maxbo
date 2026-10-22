package io.github.leonfox28.maxbo.ui.comments

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.CommentOrder
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentsPageJson
import io.github.leonfox28.maxbo.support.form
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.support.userJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CommentRepliesPageTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val graph = testGraph(restored = signedIn, web = web)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** The status page's list, below the thread. */
    private val statusComments =
        CommentsController(graph.api, "42", "1001", CommentOrder.Hot, scope)

    init {
        web.respond = { request ->
            when (request.url.encodedPath) {
                "/ajax/statuses/buildComments" ->
                    if (request.url.queryParameter("fetch_level") == "0") {
                        json(
                            commentsPageJson(listOf(commentJson(id = 20, replyCount = 2)))
                                .toString()
                        )
                    } else {
                        json(
                            commentsPageJson(
                                    listOf(
                                        commentJson(id = 21, text = "别人的回复"),
                                        commentJson(
                                            id = 22,
                                            text = "我的回复",
                                            user = userJson(id = 7, name = "我"),
                                        ),
                                    )
                                )
                                .toString()
                        )
                    }
                "/ajax/comments/reply" ->
                    json(
                        obj(
                                "ok" to 1,
                                "data" to
                                    commentJson(id = 23, text = "新的回复", user = userJson(id = 7)),
                            )
                            .toString()
                    )
                "/ajax/statuses/destroyComment" -> json("""{"ok":1}""")
                else -> MockResponse(code = 404)
            }
        }
    }

    @Before
    fun showPage() {
        graph.openLists.comments.add("42" to "1001", statusComments)
        statusComments.start()
        compose.waitUntil(5_000) { statusComments.state.value is ListLoad.Ready }
        val root = Comment.tryFromJson(commentJson(id = 20, text = "根评论", replyCount = 2))!!
        compose.setContent {
            TestHost(graph) {
                CommentRepliesPage(CommentThread(root, statusId = "42", authorId = "1001"))
            }
        }
        waitFor("别人的回复")
    }

    @After
    fun tearDown() {
        scope.cancel()
        web.close()
    }

    private fun rootOnStatusPage() =
        (statusComments.state.value as ListLoad.Ready).list.items.single()

    private fun sentForm() = web.requests.last().form

    private fun shows(text: String) =
        compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { shows(text) }

    private fun text(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)

    private fun send(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("发送").performClick()
    }

    @Test
    fun `the bar replies to the thread, newest first`() {
        text("2 条回复").assertExists()

        compose.onNodeWithText("回复 @评论者").performClick()
        compose.waitForIdle()
        send("新的回复")
        waitFor("回复成功")

        assertEquals("/ajax/comments/reply", web.requests.last().url.encodedPath)
        assertEquals("42", sentForm()["id"])
        assertEquals("20", sentForm()["cid"])
        text("3 条回复").assertExists()
        assertEquals(3, rootOnStatusPage().replyCount)
        assertEquals("新的回复", rootOnStatusPage().replies.single().text)
        val newTop = text("新的回复").fetchSemanticsNode().boundsInRoot.top
        val otherTop = text("别人的回复").fetchSemanticsNode().boundsInRoot.top
        assertTrue("$newTop vs $otherTop", newTop < otherTop)
    }

    @Test
    fun `tapping a reply replies to it`() {
        text("别人的回复").performClick()
        compose.waitForIdle()
        send("新的回复")
        waitFor("回复成功")

        assertEquals("21", sentForm()["cid"])
    }

    @Test
    fun `the user can delete their own replies`() {
        text("我的回复").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("删除").performClick()
        waitFor("确定删除这条评论吗？")
        compose.onNodeWithText("删除").performClick()
        waitFor("已删除")

        assertEquals(mapOf("cid" to "22"), sentForm())
        assertTrue(!shows("我的回复"))
        text("1 条回复").assertExists()
        assertEquals(1, rootOnStatusPage().replyCount)
    }
}

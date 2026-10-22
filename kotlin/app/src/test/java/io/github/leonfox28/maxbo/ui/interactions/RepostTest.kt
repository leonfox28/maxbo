package io.github.leonfox28.maxbo.ui.interactions

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.WithSnackbars
import io.github.leonfox28.maxbo.support.closeSheet
import io.github.leonfox28.maxbo.support.feedJson
import io.github.leonfox28.maxbo.support.form
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.plus
import io.github.leonfox28.maxbo.support.showsTextField
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.support.userJson
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepostTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val graph = testGraph(restored = signedIn, web = web)

    // A repost of post 1, by user 5, whose text lists cut short.
    private val longRepost =
        statusJson(
            id = 2,
            text = "截断的转发理由",
            user = userJson(id = 5, name = "中间人"),
            retweeted = statusJson(id = 1),
            extra = mapOf("isLongText" to true, "text_raw" to "截断的"),
        )

    @Volatile
    private var answer: () -> MockResponse = {
        json(
            obj(
                    "ok" to 1,
                    "data" to
                        obj(
                            "statuses" to
                                statusJson(
                                    id = 78,
                                    text = "转发微博",
                                    user = userJson(id = 7, name = "我"),
                                    retweeted = statusJson(id = 1),
                                )
                        ),
                )
                .toString()
        )
    }

    init {
        web.respond = { request ->
            when (request.url.encodedPath) {
                "/ajax/statuses/normal_repost" -> answer()
                "/ajax/statuses/show" ->
                    json(
                        (obj("ok" to 1) +
                                longRepost +
                                obj("longText" to obj("content" to "完整的转发理由")))
                            .toString()
                    )
                "/ajax/feed/friendstimeline" ->
                    json(feedJson(listOf(statusJson(id = 1))).toString())
                else -> MockResponse(code = 404)
            }
        }
    }

    @After fun tearDown() = web.close()

    private fun sentForm() = web.requests.last().form

    private fun field() = compose.onNode(hasSetTextAction())

    private fun fieldText() =
        field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text

    private fun sendButton() = compose.onNode(hasText("转发") and hasClickAction())

    /** A page with a button that reposts [json]. */
    private fun showButton(json: JsonObject, onReposted: () -> Unit = {}) {
        val status = Status.tryFromJson(json)!!
        compose.setContent {
            TestHost(graph) {
                WithSnackbars {
                    val writes = rememberStatusWrites()
                    TextButton(onClick = { writes.repost(status, onReposted) }) { Text("打开") }
                }
            }
        }
    }

    private fun open() {
        compose.onNodeWithText("打开").performClick()
        compose.waitUntil(5_000) { compose.showsTextField() }
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun `reposts a post for the chosen audience, and the repost goes atop the open timeline`() {
        var reposted = 0
        // Open, as the home tab keeps it.
        graph.timeline.start()
        compose.waitUntil(5_000) { graph.timeline.state.value is ListLoad.Ready }
        showButton(statusJson(id = 1), onReposted = { reposted++ })

        open()
        // An original starts empty, and goes as it is.
        assertEquals("", fieldText())
        sendButton().assertIsEnabled()
        compose.onNodeWithText("同时评论").performClick()
        compose.onNodeWithText("公开").performClick()
        compose.onNodeWithText("仅自己可见").performClick()
        sendButton().performClick()
        waitFor("转发成功")

        assertEquals("/ajax/statuses/normal_repost", web.requests.last().url.encodedPath)
        assertEquals(
            mapOf(
                "id" to "1",
                "is_comment" to "1",
                "comment_ori" to "0",
                "is_repost" to "0",
                "visible" to "1",
            ),
            sentForm(),
        )
        assertFalse(compose.showsTextField())
        assertEquals(1, reposted)
        val timeline = graph.timeline.state.value as ListLoad.Ready
        assertEquals(listOf("78", "1"), timeline.list.items.map { it.id })
    }

    @Test
    fun `a repost of a repost starts with the chain it carries on, the cursor before it`() {
        showButton(
            statusJson(
                id = 2,
                text = "<a href=\"/n/朋友\">@朋友</a> 转发理由",
                user = userJson(id = 5, name = "中间人"),
                retweeted = statusJson(id = 1),
                extra = mapOf("text_raw" to "@朋友 转发理由"),
            )
        )

        open()
        assertEquals("//@中间人:@朋友 转发理由", fieldText())
        assertEquals(
            TextRange(0),
            field().fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )
        sendButton().performClick()
        waitFor("转发成功")

        assertEquals("2", sentForm()["id"])
        assertEquals("//@中间人:@朋友 转发理由", sentForm()["comment"])
    }

    @Test
    fun `a long repost cut short in a list loads in full first`() {
        showButton(longRepost)

        open()

        assertEquals(listOf("/ajax/statuses/show"), web.requests.map { it.url.encodedPath })
        assertEquals("//@中间人:完整的转发理由", fieldText())
    }

    @Test
    fun `a failed repost stays in the sheet with the reason, and the draft outlives it`() {
        showButton(statusJson(id = 1))
        answer = { json("""{"ok":0,"msg":"转发太频繁了"}""") }

        open()
        field().performTextInput("我的看法")
        sendButton().performClick()
        waitFor("转发太频繁了")

        compose.onNodeWithText("转发成功").assertDoesNotExist()

        compose.closeSheet()
        assertFalse(compose.showsTextField())
        open()
        assertEquals("我的看法", fieldText())
    }
}

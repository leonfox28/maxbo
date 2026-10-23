package io.github.leonfox28.maxbo.ui.interactions

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.closeSheet
import io.github.leonfox28.maxbo.support.html
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.showsTextField
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.ui.compose.PicturePicker
import io.github.leonfox28.maxbo.ui.widget.LocalOverlays
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommentComposerTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val files = TemporaryFolder()

    private val web = FakeWeibo()
    private val graph = testGraph(restored = signedIn, web = web)
    @Volatile private var allowed = true
    private val uploads = AtomicInteger()
    private var picked = emptyList<String>()
    private var pickLimit: Int? = null
    private val drafts = mutableMapOf<String, CommentDraft>()

    init {
        web.respond = { request ->
            when (request.url.encodedPath) {
                "/ajax/statuses/checkAllowCommentWithPic" -> json("""{"ok":1,"result":$allowed}""")
                "/interface/pic_upload.php" ->
                    html(
                        """{"code":"A00006","data":{"pics":{"pic_1":""" +
                            """{"pid":"pid${uploads.incrementAndGet()}"}}}}"""
                    )
                else -> MockResponse(code = 404)
            }
        }
    }

    @After fun tearDown() = web.close()

    private fun paths() = web.requests.map { it.url.encodedPath }

    /** A page with a button that opens the composer, which hands what is sent to [send]. */
    private fun showButton(send: suspend (text: String, pid: String?) -> Unit) {
        val picker = PicturePicker { limit ->
            pickLimit = limit
            picked
        }
        compose.setContent {
            TestHost(graph, picturePicker = picker) {
                val overlays = LocalOverlays.current
                val scope = rememberCoroutineScope()
                TextButton(
                    onClick = {
                        scope.launch {
                            overlays.showCommentComposer("42", "写评论…", drafts, "status", send)
                        }
                    }
                ) {
                    Text("评论")
                }
            }
        }
        open()
    }

    private fun open() {
        compose.onNodeWithText("评论").performClick()
        compose.waitForIdle()
    }

    private fun sendButton() = compose.onNodeWithText("发送")

    /** Taps to add a picture; Weibo is asked first, so it waits for [until] to show. */
    private fun addPicture(until: String = "移除") {
        compose.onNodeWithContentDescription("添加图片").performClick()
        compose.waitUntil(5_000) {
            compose
                .onAllNodes(hasContentDescription(until) or hasText(until))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun `a picture alone can go, uploaded once when it is sent`() {
        picked = listOf(files.newFile("a.gif").apply { writeText("GIF89a") }.toURI().toString())
        val sent = mutableListOf<Pair<String, String?>>()
        var fail = true
        showButton { text, pid ->
            if (fail) throw WeiboApiException("评论太频繁了")
            sent += text to pid
        }
        sendButton().assertIsNotEnabled()

        addPicture()

        // Weibo is asked first whether comments here take pictures.
        assertEquals(listOf("/ajax/statuses/checkAllowCommentWithPic"), paths())
        assertEquals("42", web.requests.single().url.queryParameter("id"))
        assertEquals(1, pickLimit)
        compose.onNodeWithContentDescription("移除").assertExists()
        compose.onNodeWithContentDescription("添加图片").assertDoesNotExist()
        sendButton().assertIsEnabled()

        sendButton().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("评论太频繁了")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("GIF89a", web.requests.last().body?.utf8())
        assertEquals(1, uploads.get())

        fail = false
        sendButton().performClick()
        compose.waitUntil(5_000) { !compose.showsTextField() }

        assertEquals(1, uploads.get())
        assertEquals(listOf("" to "pid1"), sent)
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `says so when comments on the status take no pictures`() {
        allowed = false
        showButton { _, _ -> }

        addPicture(until = "这条微博的评论不能带图")

        assertNull(pickLimit)
        compose.onNodeWithContentDescription("添加图片").assertIsNotEnabled()
    }

    @Test
    fun `the picture stays with the draft, and can be taken out`() {
        picked = listOf(File("/nowhere/a.jpg").toURI().toString())
        val sent = mutableListOf<Pair<String, String?>>()
        showButton { text, pid -> sent += text to pid }
        compose.onNode(hasSetTextAction()).performTextInput("看图")
        addPicture()

        compose.closeSheet()
        assertFalse(compose.showsTextField())
        open()

        compose.onNodeWithText("看图").assertExists()
        compose.onNodeWithContentDescription("移除").performClick()
        compose.onNodeWithContentDescription("添加图片").assertExists()

        sendButton().performClick()
        compose.waitUntil(5_000) { !compose.showsTextField() }

        assertEquals(0, uploads.get())
        assertEquals(listOf("看图" to null), sent)
    }
}

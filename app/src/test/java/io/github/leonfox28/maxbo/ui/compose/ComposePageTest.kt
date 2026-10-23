package io.github.leonfox28.maxbo.ui.compose

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.WithSnackbars
import io.github.leonfox28.maxbo.support.form
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.support.userJson
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComposePageTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val graph = testGraph(restored = signedIn, web = web)
    private val navigator = Navigator()
    @Volatile private var failPost = false
    private var picked = emptyList<String>()
    private var pickLimit: Int? = null

    init {
        web.respond = { request ->
            when (request.url.encodedPath) {
                "/ajax/statuses/update" ->
                    if (failPost) json("""{"ok":0,"msg":"发布太频繁"}""")
                    else
                        json(
                            obj("ok" to 1, "data" to statusJson(id = 9, user = userJson(id = 7)))
                                .toString()
                        )
                else -> MockResponse(code = 404)
            }
        }
    }

    @After fun tearDown() = web.close()

    /** A page with a button that opens the compose page over it, as the home tab's does. */
    @Before
    fun showButton() {
        val picker = PicturePicker { limit ->
            pickLimit = limit
            picked
        }
        compose.setContent {
            TestHost(graph, navigator, picturePicker = picker) {
                WithSnackbars {
                    if (navigator.backStack.last() == Route.Compose) {
                        ComposePage()
                    } else {
                        TextButton(onClick = { navigator.push(Route.Compose) }) { Text("写") }
                    }
                }
            }
        }
        open()
    }

    private fun open() {
        compose.onNodeWithText("写").performClick()
        compose.waitForIdle()
    }

    private fun isOpen() = navigator.backStack.last() == Route.Compose

    private fun sendButton() = compose.onNodeWithText("发送")

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun addPictures() {
        compose.onNodeWithContentDescription("添加图片").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `sends what the user wrote, for whom they chose`() {
        sendButton().assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextInput("第一条微博")
        compose.onNodeWithText("公开").performClick()
        compose.onNodeWithText("仅自己可见").performClick()
        sendButton().performClick()
        waitFor("已发送")

        assertEquals(mapOf("content" to "第一条微博", "visible" to "1"), web.requests.single().form)
        assertEquals(false, isOpen())
    }

    @Test
    fun `a failed post stays with the reason, and the draft outlives the page`() {
        failPost = true
        compose.onNode(hasSetTextAction()).performTextInput("草稿")
        sendButton().performClick()
        waitFor("发布太频繁")

        assertEquals(true, isOpen())

        compose.onNodeWithContentDescription("关闭").performClick()
        compose.waitForIdle()
        open()

        compose.onNodeWithText("草稿").assertExists()
    }

    @Test
    fun `picks pictures while there is room, and removes them`() {
        picked = listOf("file:/nowhere/a.jpg", "file:/nowhere/b.jpg")

        addPictures()

        assertEquals(MAX_DRAFT_PICTURES, pickLimit)
        compose.onAllNodesWithContentDescription("移除").assertCountEquals(2)

        compose.onAllNodesWithContentDescription("移除")[0].performClick()
        compose.waitForIdle()

        assertEquals(
            listOf("file:/nowhere/b.jpg"),
            graph.composeDraft.draft.value.pictures.map { it.uri },
        )

        picked = (0 until 8).map { "file:/nowhere/$it.jpg" }
        addPictures()

        assertEquals(MAX_DRAFT_PICTURES - 1, pickLimit)
        compose.onAllNodesWithContentDescription("移除").assertCountEquals(9)
        compose.onAllNodes(hasText("最多", substring = true)).assertCountEquals(0)
        compose.onNodeWithContentDescription("添加图片").assertDoesNotExist()
    }

    @Test
    fun `says so when a picker without a limit gave too many`() {
        picked = (0 until 11).map { "file:/nowhere/$it.jpg" }

        addPictures()

        assertEquals(9, graph.composeDraft.draft.value.pictures.size)
        waitFor("最多 9 张图片，多选的 2 张没有加上")
    }
}

package io.github.leonfox28.maxbo.ui.media

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.github.leonfox28.maxbo.core.settings.AppSettings
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.picJson
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Playing a motion itself needs a real ExoPlayer and network, so these stop
// short of it; it was checked on the real session (PLAN §8).
@RunWith(RobolectricTestRunner::class)
class ImageViewerPageTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()

    @After fun tearDown() = web.close()

    private fun pic(pid: String, type: String = "pic", video: Boolean = type == "livephoto") =
        PicInfo.tryFromJson(picJson(pid, type = type, video = video, expires = 1))!!

    private fun show(
        pics: List<PicInfo>,
        statusId: String? = "5000000000000001",
        settings: AppSettings = AppSettings(),
    ) {
        val graph = testGraph(settings = settings, restored = signedIn, web = web)
        compose.setContent { TestHost(graph) { ImageViewerPage(pics, 0, statusId) } }
    }

    @Test
    fun `a live photo's mark plays it`() {
        show(listOf(pic("a", type = "livephoto")))

        compose.onNode(hasText("实况") and hasClickAction()).assertExists()
        // Not on its own, unless the user chose so.
        compose.waitForIdle()
        assertTrue(web.requests.isEmpty())
    }

    @Test
    fun `plays live photos on their own once each, when the user chose so`() {
        // The status no longer has the pictures, so their motions fail
        // before anything would play.
        web.respond = { json(statusJson(pics = listOf(picJson("other"))).toString()) }
        show(
            listOf(pic("a", type = "livephoto"), pic("b"), pic("c", type = "livephoto")),
            settings = AppSettings(autoplayLivePhotos = true),
        )
        compose.waitUntil(5_000) { web.requests.size == 1 }

        // A still picture stays still; the live photo after it plays.
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, web.requests.size)
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { web.requests.size == 2 }

        // Back to the first: it has played already.
        compose.onRoot().performTouchInput { swipeRight() }
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(2, web.requests.size)
    }

    @Test
    fun `a live photo without its video only has the mark`() {
        show(listOf(pic("a", type = "livephoto", video = false)))

        compose.onNodeWithText("实况").assertHasNoClickAction()
    }

    @Test
    fun `an expired live photo is fetched afresh from its status, and can be retried`() {
        // The status no longer has the picture.
        web.respond = { json(statusJson(pics = listOf(picJson("other"))).toString()) }
        show(listOf(pic("a", type = "livephoto")))

        compose.onNode(hasText("实况") and hasClickAction()).performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("实况加载失败，点击重试")).fetchSemanticsNodes().isNotEmpty()
        }
        val request = web.requests.single()
        assertEquals("/ajax/statuses/show", request.url.encodedPath)
        assertEquals("5000000000000001", request.url.queryParameter("id"))

        compose.onNodeWithText("实况加载失败，点击重试").performClick()
        compose.waitUntil(5_000) { web.requests.size == 2 }
    }
}

package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.toSize
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.picJson
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.ui.widget.status.StatusCard
import io.github.leonfox28.maxbo.ui.widget.status.StatusCardActions
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cards [WideCardFrom] or more across, at one pixel to the dp, as the Flutter tests had them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1000dp-h800dp-mdpi")
// Text measured with real fonts: legacy graphics count a pixel a character.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusCardWideTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        json: JsonObject,
        actions: StatusCardActions = StatusCardActions(),
        detail: Boolean = false,
    ) {
        val status = Status.tryFromJson(json)!!
        compose.setContent {
            TestHost(testGraph()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StatusCard(status, Modifier.testTag("card"), actions = actions, detail = detail)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun rect(text: String, substring: Boolean = false): Rect =
        compose
            .onNode(hasText(text, substring = substring), useUnmergedTree = true)
            .fetchSemanticsNode()
            .unclipped

    // As Flutter measures, beyond the edge of the screen too.
    private val SemanticsNode.unclipped: Rect
        get() = Rect(positionInRoot, size.toSize())

    /** The pictures, all together. */
    private fun grid(): Rect =
        compose
            .onAllNodesWithTag("media", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.unclipped }
            .reduce { a, b ->
                Rect(a.left, a.top, maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
            }

    private fun card() = compose.onNodeWithTag("card").fetchSemanticsNode().unclipped

    @Test
    fun `pictures sit beside the text, counts at the foot`() {
        show(statusJson(text = "配图的微博", pics = (0 until 9).map { picJson("p$it") }))

        val text = rect("配图的微博")
        val grid = grid()
        val likes = rect("赞")
        System.err.println(
            "DEBUGZ " +
                compose
                    .onAllNodesWithTag("media", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .map { it.unclipped }
        )
        assertEquals(text.left + 400 + 24, grid.left)
        assertEquals(1000f - 16 - 400 - 24 - 16, grid.width, 1f)
        assertTrue(grid.top < text.bottom)
        // The pictures run on down beside the counts, which keep to the text
        // side at the foot of the card.
        assertTrue(likes.right < grid.left)
        assertTrue(likes.top > text.bottom + 100)
        assertTrue(likes.top < grid.bottom)
        assertEquals(12f, card().bottom - grid.bottom, 1f)
    }

    @Test
    fun `taps reach the pictures and the card`() {
        val pictures = mutableListOf<Int>()
        val opened = mutableListOf<String>()
        show(
            statusJson(id = 1, text = "配图的微博", pics = listOf(picJson("a"), picJson("b"))),
            StatusCardActions(
                onPicture = { _, i, _ -> pictures += i },
                onOpen = { opened += it.id },
            ),
        )

        compose.onAllNodesWithTag("media", useUnmergedTree = true)[0].performClick()
        val target = rect("配图的微博").center - card().topLeft
        compose.onNodeWithTag("card").performTouchInput { click(target) }

        assertEquals(listOf(0), pictures)
        assertEquals(listOf("1"), opened)
    }

    @Test
    fun `counts follow text that runs longer than the pictures`() {
        show(statusJson(text = "很长的文字".repeat(200), pics = listOf(picJson("a"), picJson("b"))))

        val text = rect("很长的文字", substring = true)
        val likes = rect("赞")
        assertTrue(likes.top > text.bottom)
        assertTrue(likes.top < text.bottom + 20)
    }

    @Test
    @Config(qualifiers = "w850dp-h800dp-mdpi")
    fun `so on an unfolded foldable, with less to the pictures`() {
        // Three, which fill the grid's width.
        show(statusJson(text = "很长的文字".repeat(60), pics = (0 until 3).map { picJson("p$it") }))

        val text = rect("很长的文字", substring = true)
        val grid = grid()
        assertTrue(grid.top < text.bottom)
        assertTrue(text.width <= 400)
        assertEquals(850f - 16 - 400 - 24 - 16, grid.width, 1f)
    }

    @Test
    fun `the retweeted post sits beside the retweet`() {
        show(statusJson(text = "转发理由", retweeted = statusJson(id = 2, text = "原文内容")))

        val reason = rect("转发理由")
        val source = compose.onNodeWithTag("retweet").fetchSemanticsNode().unclipped
        assertTrue(source.left > reason.left + 400)
        assertTrue(source.top < reason.bottom)
    }

    @Test
    fun `text alone keeps to the left, in reading-length lines`() {
        show(statusJson(text = "很长的文字".repeat(60)))

        val text = rect("很长的文字", substring = true)
        val likes = rect("赞")
        assertEquals(16f, text.left)
        assertTrue("${text.right}", text.right > 16 + 600)
        assertTrue(text.right <= 16 + 640)
        assertTrue(likes.right <= 16 + 640)
    }

    @Test
    fun `not on the detail page`() {
        show(
            statusJson(text = "配图的微博", pics = listOf(picJson("a"), picJson("b"))),
            detail = true,
        )

        assertTrue(grid().top > rect("配图的微博").bottom)
    }
}

package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** At one pixel to the dp, as the Flutter tests had it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "mdpi")
class AdaptiveLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun rect(tag: String): Rect =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun `window sizes follow Material 3`() {
        assertEquals(WindowSize.Compact, WindowSize.fromWidth(599.dp))
        assertEquals(WindowSize.Medium, WindowSize.fromWidth(600.dp))
        assertEquals(WindowSize.Medium, WindowSize.fromWidth(839.dp))
        assertEquals(WindowSize.Expanded, WindowSize.fromWidth(840.dp))
    }

    @Test
    fun `reading padding centres the column, never below zero`() {
        fun side(width: Int) = readingPadding(width.dp).calculateLeftPadding(LayoutDirection.Ltr)

        assertEquals(0.dp, side(400))
        assertEquals(0.dp, side(640))
        assertEquals(180.dp, side(1000))
        // A timeline is wider on an expanded window.
        assertEquals(
            0.dp,
            readingPadding(1000.dp, feed = true).calculateLeftPadding(LayoutDirection.Ltr),
        )
    }

    private fun showPanes() {
        compose.setContent {
            ReadingPanes(
                start = { Box(Modifier.fillMaxSize().testTag("start")) },
                end = { Box(Modifier.fillMaxSize().testTag("end")) },
            )
        }
    }

    @Test
    @Config(qualifiers = "w1001dp-h800dp")
    fun `reading panes split a narrower window between them`() {
        showPanes()

        assertEquals(0f, rect("start").left)
        assertEquals(500f, rect("start").width)
        assertEquals(501f, rect("end").left)
        assertEquals(500f, rect("end").width)
    }

    @Test
    @Config(qualifiers = "w1481dp-h800dp")
    fun `reading panes are reading columns, centred together`() {
        showPanes()

        assertEquals(100f, rect("start").left)
        assertEquals(640f, rect("start").width)
        assertEquals(100f + 640 + 1, rect("end").left)
        assertEquals(640f, rect("end").width)
    }

    private fun showPill() {
        compose.setContent {
            Scaffold(
                bottomBar = {
                    FloatingPill(stretch = true) {
                        Text("写评论…", Modifier.fillMaxWidth().testTag("inside"))
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding))
            }
        }
    }

    // The pill's content sits 8 in from its edges.
    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun `a stretched pill spans a narrow window, less a margin`() {
        showPill()

        assertEquals(400f - 32 - 16, rect("inside").width)
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `a stretched pill spans the column, not a wide window`() {
        showPill()

        assertEquals(640f - 32 - 16, rect("inside").width)
        assertEquals(500f, rect("inside").center.x)
    }
}

package io.github.leonfox28.maxbo.ui.notifications

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import io.github.leonfox28.maxbo.core.settings.TextSize
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The notification tabs on a narrow phone, at one pixel to the dp. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-mdpi")
// Text measured with real fonts: legacy graphics count a pixel a character.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoticeTabsTest {
    @get:Rule val compose = createComposeRule()

    private val labels = listOf("评论", "@我的微博", "@我的评论", "赞")

    /** The page at text [size], on a device whose own font size is [deviceFontScale]. */
    private fun show(size: TextSize, deviceFontScale: Float = 1f) {
        compose.setContent {
            // As MaxboApp applies the text size.
            val device = Density(LocalDensity.current.density, deviceFontScale)
            CompositionLocalProvider(LocalDensity provides size.applyTo(device)) {
                TestHost(testGraph(restored = signedIn)) { NotificationsPage(PaddingValues()) }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(label: String, unmerged: Boolean = false): Rect {
        val node =
            compose
                .onNodeWithContentDescription(label, useUnmergedTree = unmerged)
                .fetchSemanticsNode()
        return Rect(node.positionInRoot, node.size.toSize())
    }

    private fun tab(label: String) = bounds(label)

    /** The label itself, as tall as one line of it unless it wraps. */
    private fun label(label: String) = bounds(label, unmerged = true)

    private fun assertOneLineEach() {
        val line = label("赞").height
        for (it in labels) assertEquals(it, line, label(it).height, 0.5f)
    }

    @Test
    fun `tabs share the width evenly while every label fits in a share`() {
        show(TextSize.ExtraSmall)

        for (it in labels) assertEquals(it, 90f, tab(it).width, 0.5f)
        assertOneLineEach()
    }

    @Test
    fun `larger text keeps every label on one line, the short ones giving up room`() {
        // "@我的微博" alone would wrap in a quarter of the width.
        show(TextSize.Large)

        assertOneLineEach()
        val widths = labels.map { tab(it).width }
        assertEquals(360f, widths.sum(), 0.5f)
        assertTrue(widths.toString(), widths[1] > widths[0] && widths[2] > widths[3])
        assertEquals(0f, tab("评论").left, 0.5f)
    }

    @Test
    @Config(qualifiers = "w425dp-h900dp-440dpi")
    fun `larger text keeps every label on one line on a dense screen`() {
        // At one pixel to the dp, text sizes that differ by a fraction of a
        // pixel are set alike.
        show(TextSize.Large)

        assertOneLineEach()
    }

    @Test
    fun `text too large for the width scrolls the tabs`() {
        show(TextSize.ExtraLarge, deviceFontScale = 2f)

        assertOneLineEach()
        assertTrue(tab("赞").right > 360f)
        compose.onNodeWithContentDescription("赞").performScrollTo()
        assertEquals(360f, tab("赞").right, 0.5f)
    }

    @Test
    fun `shares out what is left over to the narrowest`() {
        // Everything fits an even share.
        assertEquals(listOf(90, 90, 90, 90), shareWidth(listOf(64, 80, 80, 48), 360))
        // The widest keep their own, the others even up.
        assertEquals(listOf(74, 106, 106, 74), shareWidth(listOf(64, 106, 106, 48), 360))
        // Left over pixels go one each.
        assertEquals(listOf(5, 4, 4), shareWidth(listOf(1, 1, 1), 13))
        // Too wide altogether: each its own.
        assertEquals(listOf(120, 200, 200, 60), shareWidth(listOf(120, 200, 200, 60), 360))
    }
}

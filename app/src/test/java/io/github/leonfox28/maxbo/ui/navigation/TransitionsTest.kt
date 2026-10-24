package io.github.leonfox28.maxbo.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.SinglePaneSceneStrategy
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The transitions MaxboApp uses, between two pages of plain colour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransitionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Page(val color: Color)

    private val below = Page(Color.Blue)
    private val top = Page(Color.Red)
    private val backStack = mutableStateListOf(below, top)

    @Before
    fun setUp() {
        compose.setContent {
            PageStack(
                backStack = backStack,
                onBack = { backStack.removeAt(backStack.lastIndex) },
                dark = false,
                entryDecorators = emptyList(),
                sceneStrategies = listOf(SinglePaneSceneStrategy()),
                entryProvider = { page: Page ->
                    NavEntry(page) { Box(Modifier.fillMaxSize().background(page.color)) }
                },
            )
        }
    }

    private val dispatcher
        get() = compose.activity.onBackPressedDispatcher

    private val dp
        get() = compose.density.density

    /**
     * Starts the back gesture from [edge] at [startY], and moves the finger to [progress] and [y].
     */
    private fun swipe(
        progress: Float,
        edge: Int = BackEventCompat.EDGE_LEFT,
        startY: Float = 100f,
        y: Float = startY,
    ) {
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, startY, 0f, edge))
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, y, progress, edge))
        }
        compose.waitForIdle()
    }

    private fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The colour in the middle of the screen. */
    private fun middle(): Color = screen().let { it[it.width / 2, it.height / 2] }

    /** Where the top page reaches, through the middle of the screen: left, top, right, bottom. */
    private fun topPage(pixels: PixelMap = screen()): List<Int> {
        val across = (0 until pixels.width).filter { pixels[it, pixels.height / 2] == Color.Red }
        val down = (0 until pixels.height).filter { pixels[pixels.width / 2, it] == Color.Red }
        return listOf(across.first(), down.first(), across.last() + 1, down.last() + 1)
    }

    private fun assertNear(expected: Float, actual: Int) =
        assertTrue("expected $expected, was $actual", abs(expected - actual) <= 1f)

    @Test
    fun `the page under the finger shrinks to 90 percent, short of the far edge by 8dp`() {
        swipe(1f)

        val pixels = screen()
        val (left, top, right, bottom) = topPage(pixels)
        val width = pixels.width
        val height = pixels.height
        assertNear(width - 8 * dp - width * 0.9f, left)
        assertNear(width - 8 * dp, right)
        assertNear(height * 0.05f, top)
        assertNear(height * 0.95f, bottom)
        // The page below, under the scrim.
        val revealed = pixels[left / 2, height / 2]
        assertEquals(0f, revealed.red, 0.02f)
        assertEquals(0.8f, revealed.blue, 0.02f)
        assertEquals(listOf(below, this.top), backStack.toList())
    }

    @Test
    fun `swiped in from the right edge, the page shrinks in place`() {
        swipe(1f, edge = BackEventCompat.EDGE_RIGHT)

        val pixels = screen()
        val (left, _, right, _) = topPage(pixels)
        assertNear(pixels.width * 0.05f, left)
        assertNear(pixels.width * 0.95f, right)
    }

    @Test
    fun `the page follows the finger down, as far as 8dp from the bottom`() {
        val height = screen().height
        swipe(1f, startY = 100f, y = 100f + height / 2)

        val (_, top, _, bottom) = topPage()
        assertNear(height * 0.1f - 8 * dp, top)
        assertNear(height - 8 * dp, bottom)
    }

    @Test
    fun `swiping back to the edge keeps the page`() {
        swipe(1f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()

        assertEquals(listOf(0, 0, screen().width, screen().height), topPage())
        assertEquals(listOf(below, top), backStack.toList())
    }

    @Test
    fun `let go, the page fades out as the one below grows back`() {
        swipe(0.3f)
        val shrunk = belowRight()

        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.onBackPressed() }
        frames(4)
        val fading = middle()
        assertNotEquals(Color.Red, fading)
        assertNotEquals(Color.Blue, fading)
        frames(12)
        assertTrue("grew from $shrunk to ${belowRight()}", belowRight() > shrunk + 8 * dp)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settled = screen()
        assertEquals(Color.Blue, settled[0, 0])
        assertEquals(Color.Blue, settled[settled.width - 1, settled.height - 1])
        assertEquals(listOf(below), backStack.toList())
    }

    /** Where the page below reaches to the right, through the middle of the screen. */
    private fun belowRight(): Int {
        val pixels = screen()
        return (0 until pixels.width).last {
            pixels[it, pixels.height / 2].let { colour -> colour.blue > 0.5f && colour.red < 0.2f }
        }
    }

    private fun frames(count: Int) =
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

    @Test
    fun `a page closed by a back press fades out`() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.onBackPressed() }
        // The fade starts a few frames in, and lasts 112 ms.
        frames(8)
        val fading = middle()
        assertNotEquals(Color.Red, fading)
        assertNotEquals(Color.Blue, fading)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(Color.Blue, middle())
        assertEquals(listOf(below), backStack.toList())
    }
}

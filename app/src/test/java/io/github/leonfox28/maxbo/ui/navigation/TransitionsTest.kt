package io.github.leonfox28.maxbo.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeAt(backStack.lastIndex) },
                transitionSpec = pushTransition(),
                popTransitionSpec = popTransition(),
                predictivePopTransitionSpec = predictivePopTransition(),
                entryProvider =
                    { page: Page ->
                            NavEntry(page) { Box(Modifier.fillMaxSize().background(page.color)) }
                        }
                        .fadingOnceClosed(backStack),
            )
        }
    }

    private val dispatcher
        get() = compose.activity.onBackPressedDispatcher

    private fun back(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    /** Starts the back gesture and moves the finger to [progress]. */
    private fun swipe(progress: Float) {
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(progress))
        }
        compose.waitForIdle()
    }

    /** The colour in the middle of the screen. */
    private fun middle(): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    @Test
    fun `the page stays whole under the finger, however far it goes`() {
        swipe(1f)

        assertEquals(Color.Red, middle())
        assertEquals(listOf(below, top), backStack.toList())
    }

    @Test
    fun `the page fades once let go`() {
        swipe(0.3f)
        assertEquals(Color.Red, middle())

        closesFading()
    }

    @Test
    fun `swiping back to the edge keeps the page`() {
        swipe(1f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()

        assertEquals(Color.Red, middle())
        assertEquals(listOf(below, top), backStack.toList())
    }

    @Test
    fun `a page closed by a back press fades too`() {
        closesFading()
    }

    /** Lets go of the gesture (or presses back), and sees the page fade out over the one below. */
    private fun closesFading() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.onBackPressed() }
        // The fade starts a few frames in, and lasts 112 ms.
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        val fading = middle()
        assertNotEquals(Color.Red, fading)
        assertNotEquals(Color.Blue, fading)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(Color.Blue, middle())
        assertEquals(listOf(below), backStack.toList())
    }
}

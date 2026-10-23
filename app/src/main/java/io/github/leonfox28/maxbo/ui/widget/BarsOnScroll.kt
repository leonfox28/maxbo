package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp

/**
 * Whether bars that give way to reading should show: they hide while the user scrolls down a list
 * and come back when they scroll up or reach its top. Install [connection] with `nestedScroll` on a
 * parent of the lists.
 *
 * @param threshold scrolling this far one way, in pixels, shows or hides the bars. Lifting a finger
 *   from a fling can report a few pixels the other way, which mustn't bring them back.
 */
class BarsOnScroll(private val threshold: Float) {
    var visible by mutableStateOf(true)
        private set

    /** How far the list has scrolled since it last turned: positive down. */
    private var scrolled = 0f

    val connection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Only the user's own dragging counts. Hiding a bar can make
                // the list taller and pull it back from its end, which
                // mustn't show the bar.
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                onScroll(delta = -consumed.y, atTop = available.y > 0)
                return Offset.Zero
            }
        }

    /** Takes in a scroll of [delta] (positive down); [atTop] when the list can't go further up. */
    fun onScroll(delta: Float, atTop: Boolean) {
        if (atTop) {
            show()
            return
        }
        if (delta == 0f) return
        if ((delta > 0) != (scrolled > 0)) scrolled = 0f
        scrolled += delta
        if (kotlin.math.abs(scrolled) >= threshold) visible = scrolled < 0
    }

    fun show() {
        visible = true
        scrolled = 0f
    }

    companion object {
        /** [threshold] in dp. */
        val Threshold = 24.dp

        const val DURATION = 250
        val Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

        fun <T> spec() = tween<T>(DURATION, easing = Easing)
    }
}

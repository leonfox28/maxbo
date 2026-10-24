package io.github.leonfox28.maxbo.ui.navigation

import android.os.Build
import android.os.SystemClock
import android.view.RoundedCorner
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Android's own back animation between two activities (DefaultCrossActivityBackAnimation in AOSP's
// WindowManager Shell), for two pages. While the finger is down, the page under it shrinks to 90%,
// drifting from the left edge to 8dp short of the right one (or in place, from the right edge),
// and up or down with the finger; the page it reveals waits 96dp to the left under a scrim,
// shrinking with it. Let go to go back, and the page fades out moving on to the right as the one
// below grows back to the whole screen.

/** The smallest the pages get, as a fraction of the screen. */
private const val MIN_SCALE = 0.9f

/** How close to the screen's edges the page under the finger comes. */
private val Margin = 8.dp

/** How far left the page below waits. */
private val BelowOffset = 96.dp

/** How long the pages take to settle once let go. */
private const val SETTLE_DURATION = 450

private const val SCRIM_LIGHT = 0.2f
private const val SCRIM_DARK = 0.8f

/** The gesture's progress to the pages': quick at first, then slowing down. */
private val GestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * Material's emphasized easing in full, as the pages settle with: two cubic curves, meeting at
 * (0.166666, 0.4). PathEasing would draw the same, but needs a native library tests don't have.
 */
private val SettleEasing = Easing { x ->
    if (x < 0.166666f) cubicAt(x, 0f, 0f, 0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
    else cubicAt(x, 0.166666f, 0.4f, 0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
}

/** Where the cubic Bézier curve from (x0, y0) to (x3, y3) reaches [x], its x rising all along. */
private fun cubicAt(
    x: Float,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    x3: Float,
    y3: Float,
): Float {
    fun at(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
        val u = 1 - t
        return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
    }
    var low = 0f
    var high = 1f
    repeat(24) {
        val t = (low + high) / 2
        if (at(t, x0, x1, x2, x3) < x) low = t else high = t
    }
    return at((low + high) / 2, y0, y1, y2, y3)
}

/** A fling shrinks both pages a little further as they settle, and they bounce back. */
private val FlingSpring = spring<Float>(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)

/** Android's default timing for an animator, which the system settles the pages over. */
private fun accelerateDecelerate(time: Float) = (cos((time + 1) * PI) / 2 + 0.5).toFloat()

/**
 * Moves the page under the back gesture and the one it reveals, as Android moves two activities.
 * [PageStack] feeds it the gesture and draws the pages where it puts them.
 */
@Stable
class PredictiveBack(private val scope: CoroutineScope, private val view: View) {
    private enum class Phase {
        Idle,
        Following,
        Closing,
        Returning,
    }

    private var phase by mutableStateOf(Phase.Idle)
    private var top: Any? by mutableStateOf(null)
    private var below: Any? by mutableStateOf(null)

    /** The gesture's progress, as the system gives it. */
    private var progress by mutableFloatStateOf(0f)
    private var touchY by mutableFloatStateOf(0f)
    private var startY = 0f
    private var edge = NavigationEvent.EDGE_LEFT
    private var corners: Shape = RectangleShape
    private val velocity = VelocityTracker1D(isDataDifferential = false)

    /** How far through settling the pages are, in time. */
    private var settled by mutableFloatStateOf(0f)
    private var flingScale by mutableFloatStateOf(1f)
    private var job: Job? = null

    /** Follows the back gesture, [state], over the last page of [backStack] and the one before. */
    internal fun follow(state: NavigationEventTransitionState, backStack: List<Any>) {
        if (state !is NavigationEventTransitionState.InProgress || backStack.size < 2) return
        val event = state.latestEvent
        if (phase != Phase.Following) {
            job?.cancel()
            top = backStack.last()
            below = backStack[backStack.lastIndex - 1]
            startY = event.touchY
            edge = event.swipeEdge
            corners = windowCorners()
            velocity.resetTracking()
            settled = 0f
            flingScale = 1f
            phase = Phase.Following
        }
        progress = event.progress
        touchY = event.touchY
        // Before Android 16, the gesture has no frame times; this follows it once a frame.
        val time = event.frameTimeMillis.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
        velocity.addDataPoint(time, GestureEasing.transform(progress))
    }

    /** The gesture let go to close the top page. */
    internal fun closed() {
        if (phase != Phase.Following) return
        phase = Phase.Closing
        // In the system's units, where 100 is the whole of a page's size; a swipe in from the
        // side counts twice.
        val sides = if (edge == NavigationEvent.EDGE_NONE) 1f else 2f
        val measured = velocity.calculateVelocity().takeIf { it.isFinite() } ?: 0f
        var fling = measured * 100 * (1 - MIN_SCALE) * sides
        if (GestureEasing.transform(progress) < 0.1f) fling = max(fling, 120f)
        fling = fling.coerceIn(0f, 1000f)
        job = scope.launch {
            val bounce = launch {
                animate(100f, 100f, -fling, FlingSpring) { value, _ ->
                    flingScale = min(value / 100, 1f)
                }
            }
            animate(0f, 1f, animationSpec = tween(SETTLE_DURATION, easing = LinearEasing)) {
                value,
                _ ->
                settled = value
            }
            bounce.cancel()
            stop()
        }
    }

    /** The gesture let go where it began, keeping the top page. */
    internal fun returned() {
        if (phase != Phase.Following) return
        phase = Phase.Returning
        // The system has mostly brought the progress back already.
        job = scope.launch {
            animate(progress, 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) {
                value,
                _ ->
                progress = value
            }
            stop()
        }
    }

    private fun stop() {
        phase = Phase.Idle
        top = null
        below = null
    }

    /** Whether [page] is the one revealed, to go over the background and under the scrim. */
    internal fun reveals(page: Any): Boolean = phase != Phase.Idle && page === below

    /** How dark the scrim over the page revealed is. */
    internal fun scrimAlpha(dark: Boolean): Float {
        val alpha = if (dark) SCRIM_DARK else SCRIM_LIGHT
        return if (phase == Phase.Closing) alpha * (1 - accelerateDecelerate(settled)) else alpha
    }

    /** Puts [page] where the gesture has it, if it's one of the two it moves, and says whether. */
    internal fun GraphicsLayerScope.move(page: Any): Boolean {
        if (phase == Phase.Idle || size.isEmpty()) return false
        val isTop = page === top
        if (!isTop && page !== below) return false
        val screen = Rect(Offset.Zero, size)
        val margin = Margin.toPx()
        val offset = BelowOffset.toPx()
        val eased = GestureEasing.transform(progress)
        var shrunk = screen.scaledBy(MIN_SCALE)
        if (edge != NavigationEvent.EDGE_RIGHT) {
            shrunk = shrunk.translate(screen.right - shrunk.right - margin, 0f)
        }
        val topRect = lerp(screen, shrunk, eased)
        val dy = shiftY(topRect, screen, margin)
        var rect =
            if (isTop) {
                topRect.translate(0f, dy)
            } else {
                val start = screen.translate(-offset, 0f)
                lerp(start, start.scaledBy(MIN_SCALE), eased).translate(0f, dy)
            }
        if (phase == Phase.Closing) {
            val time = accelerateDecelerate(settled)
            val end = if (isTop) screen.translate(rect.left + offset, 0f) else screen
            rect = lerp(rect, end, SettleEasing.transform(time)).scaledBy(flingScale)
            if (isTop) alpha = max(1 - time * 5, 0f)
        }
        scaleX = rect.width / screen.width
        scaleY = rect.height / screen.height
        translationX = rect.center.x - screen.center.x
        translationY = rect.center.y - screen.center.y
        shape = corners
        clip = true
        // Drawn once and scaled as a whole, as the system scales windows: text redrawn at each
        // new scale would cost frames.
        compositingStrategy = CompositingStrategy.Offscreen
        return true
    }

    /** Up or down with the finger, slowing down, and never past [margin] from the screen's edge. */
    private fun shiftY(rect: Rect, screen: Rect, margin: Float): Float {
        val half = screen.height / 2
        val moved = touchY - startY
        val ratio = min(half, abs(moved)) / half
        val decelerated = 1 - (1 - ratio) * (1 - ratio)
        val room = max(0f, (screen.height - rect.height) / 2 - margin)
        return room * decelerated * (if (moved < 0) -1 else 1)
    }

    /** The display's rounded corners, which the system gives windows as they move. */
    private fun windowCorners(): Shape {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return RectangleShape
        val insets = view.rootWindowInsets ?: return RectangleShape
        val radius =
            listOf(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_BOTTOM_LEFT).minOf {
                insets.getRoundedCorner(it)?.radius ?: 0
            }
        return if (radius > 0) RoundedCornerShape(radius.toFloat()) else RectangleShape
    }
}

private fun Rect.scaledBy(scale: Float): Rect {
    val halfWidth = width * scale / 2
    val halfHeight = height * scale / 2
    return Rect(
        center.x - halfWidth,
        center.y - halfHeight,
        center.x + halfWidth,
        center.y + halfHeight,
    )
}

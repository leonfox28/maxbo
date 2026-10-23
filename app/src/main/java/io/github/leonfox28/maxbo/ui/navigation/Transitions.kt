package io.github.leonfox28.maxbo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

// Android's page transitions, as Flutter has them (and the Flutter app had):
// pages slide a quarter of the way in while fading in, and the predictive
// back gesture shrinks the page under the finger to reveal the one below.
// A page closing fades out apart from these (fadingOnceClosed()).

private const val DURATION = 450
private const val PREDICTIVE_DURATION = 400

/** Material's emphasized easing. */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** The page coming in fades in over the first three quarters of the transition. */
private fun pageFadeIn() = fadeIn(tween(DURATION * 3 / 4, easing = LinearEasing))

/** The page going out fades out over the first quarter. */
private fun <T> fadingOut() = tween<T>(DURATION / 4, easing = LinearEasing)

private fun pageFadeOut() = fadeOut(fadingOut())

private fun <T> slide() = tween<T>(DURATION, easing = Emphasized)

fun <T : Any> pushTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    (pageFadeIn() + slideInHorizontally(slide()) { it / 4 }) togetherWith
        (pageFadeOut() + slideOutHorizontally(slide()) { -it / 4 })
}

/** The closed page fades out in [fadingOnceClosed]. */
fun <T : Any> popTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    (pageFadeIn() + slideInHorizontally(slide()) { -it / 4 }) togetherWith
        slideOutHorizontally(slide()) { it / 4 }
}

/**
 * Follows the back gesture's progress: the page shrinks to 90% and drifts away from the edge being
 * swiped, over the page it reveals. It fades once let go, in [fadingOnceClosed]: Navigation 3 seeks
 * this transition by the gesture's progress, so a fade in here would show under the finger, and a
 * swipe across the screen would leave nothing there.
 */
fun <T : Any> predictivePopTransition():
    AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = { edge ->
    val away = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1
    EnterTransition.None togetherWith
        (scaleOut(tween(PREDICTIVE_DURATION, easing = LinearEasing), targetScale = 0.9f) +
            slideOutHorizontally(tween(PREDICTIVE_DURATION, easing = LinearEasing)) {
                away * it / 20
            })
}

/**
 * These entries, each fading out once its page is closed (off [backStack]), whether by a back press
 * or by letting go of the back gesture.
 */
fun <T : Any> ((T) -> NavEntry<T>).fadingOnceClosed(backStack: List<T>): (T) -> NavEntry<T> =
    { key ->
        val entry = this(key)
        NavEntry(navEntry = entry) {
            // By identity: the same page can be open twice.
            val closed by remember(key) { derivedStateOf { backStack.none { it === key } } }
            FadesOut(closed) { entry.Content() }
        }
    }

@Composable
private fun FadesOut(out: Boolean, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(if (out) 0f else 1f, fadingOut(), label = "page alpha")
    Box(Modifier.graphicsLayer { this.alpha = alpha }, propagateMinConstraints = true) { content() }
}

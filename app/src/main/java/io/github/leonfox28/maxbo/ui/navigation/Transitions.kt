package io.github.leonfox28.maxbo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

// Android's page transitions, as Flutter has them (and the Flutter app had):
// pages slide a quarter of the way in while fading in, and the predictive
// back gesture shrinks the page under the finger to reveal the one below.

private const val DURATION = 450
private const val PREDICTIVE_DURATION = 400

/** Material's emphasized easing. */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** The page coming in fades in over the first three quarters of the transition. */
private fun pageFadeIn() = fadeIn(tween(DURATION * 3 / 4, easing = LinearEasing))

/** The page going out fades out over the first quarter. */
private fun pageFadeOut() = fadeOut(tween(DURATION / 4, easing = LinearEasing))

private fun <T> slide() = tween<T>(DURATION, easing = Emphasized)

fun <T : Any> pushTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    (pageFadeIn() + slideInHorizontally(slide()) { it / 4 }) togetherWith
        (pageFadeOut() + slideOutHorizontally(slide()) { -it / 4 })
}

fun <T : Any> popTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    (pageFadeIn() + slideInHorizontally(slide()) { -it / 4 }) togetherWith
        (pageFadeOut() + slideOutHorizontally(slide()) { it / 4 })
}

/**
 * Follows the back gesture's progress: the page shrinks to 90% and drifts away from the edge being
 * swiped, over the page it reveals. It fades only near the end, which the finger seldom reaches, so
 * it mostly fades once let go.
 */
fun <T : Any> predictivePopTransition():
    AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = { edge ->
    val away = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1
    EnterTransition.None togetherWith
        (scaleOut(tween(PREDICTIVE_DURATION, easing = LinearEasing), targetScale = 0.9f) +
            slideOutHorizontally(tween(PREDICTIVE_DURATION, easing = LinearEasing)) {
                away * it / 20
            } +
            fadeOut(
                keyframes {
                    durationMillis = PREDICTIVE_DURATION
                    1f at PREDICTIVE_DURATION * 6 / 10
                    0f at PREDICTIVE_DURATION
                }
            ))
}

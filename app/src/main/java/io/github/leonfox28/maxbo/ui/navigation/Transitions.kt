package io.github.leonfox28.maxbo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene

// Android's page transitions, as Flutter has them (and the Flutter app had): pages slide a quarter
// of the way in while fading in. The back gesture moves pages as Android moves activities
// (PredictiveBack), and a page closed otherwise fades out apart from these (PageStack).

private const val DURATION = 450

/**
 * How long Navigation 3 keeps the page below for the back gesture, as a whole. It seeks the
 * transition by the gesture's progress and plays the rest once let go, so this also keeps the page
 * closed for (1 - progress) of it: long enough to fade out, unless let go across the screen.
 */
private const val PREDICTIVE_DURATION = 700

/** Material's emphasized easing. */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** The page coming in fades in over the first three quarters of the transition. */
private fun pageFadeIn() = fadeIn(tween(DURATION * 3 / 4, easing = LinearEasing))

/** The page going out fades out over the first quarter. */
internal fun <T> fadingOut() = tween<T>(DURATION / 4, easing = LinearEasing)

private fun pageFadeOut() = fadeOut(fadingOut())

private fun <T> slide() = tween<T>(DURATION, easing = Emphasized)

fun <T : Any> pushTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    (pageFadeIn() + slideInHorizontally(slide()) { it / 4 }) togetherWith
        (pageFadeOut() + slideOutHorizontally(slide()) { -it / 4 })
}

/** The closed page fades out in [PageStack]. */
fun <T : Any> popTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    (pageFadeIn() + slideInHorizontally(slide()) { -it / 4 }) togetherWith
        slideOutHorizontally(slide()) { it / 4 }
}

/**
 * Keeps both pages through the back gesture, for [PredictiveBack] to move, by shrinking the page
 * under the finger by less than a pixel over [PREDICTIVE_DURATION]. Something has to change for the
 * transition to last at all, and a fade would draw the page offscreen while it moves.
 */
fun <T : Any> predictivePopTransition():
    AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = {
    EnterTransition.None togetherWith
        scaleOut(tween(PREDICTIVE_DURATION, easing = LinearEasing), targetScale = 0.9999f)
}

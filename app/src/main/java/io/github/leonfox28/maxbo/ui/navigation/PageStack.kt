package io.github.leonfox28.maxbo.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationEventState

/**
 * The pages of [backStack] in a [NavDisplay], with our transitions, and the back gesture moving
 * them as [PredictiveBack] has it. A page closed otherwise fades out (on top of [popTransition]).
 *
 * What [NavDisplay] does with a back stack, but with its back gesture in hand: [PredictiveBack]
 * follows that one alone, not one a page handles itself.
 *
 * @param dark for the scrim over the page the gesture reveals.
 */
@Composable
fun <T : Any> PageStack(
    backStack: List<T>,
    onBack: () -> Unit,
    dark: Boolean,
    entryDecorators: List<NavEntryDecorator<T>>,
    sceneStrategies: List<SceneStrategy<T>>,
    entryProvider: (T) -> NavEntry<T>,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val back = remember { PredictiveBack(scope, view) }
    val entries =
        rememberDecoratedNavEntries(
            backStack,
            entryDecorators,
            entryProvider.moving(backStack, back, dark),
        )
    val sceneState = rememberSceneState(entries, sceneStrategies, emptyList(), null, onBack)
    val gesture = rememberNavigationEventState(sceneState)
    NavigationBackHandler(
        sceneState,
        gesture,
        onBackCancelled = back::returned,
        onBackCompleted = {
            back.closed()
            onBack()
        },
    )
    Follows(back, gesture, backStack)
    NavDisplay(
        sceneState,
        gesture,
        transitionSpec = pushTransition(),
        popTransitionSpec = popTransition(),
        predictivePopTransitionSpec = predictivePopTransition(),
    )
}

/** Hands [back] the gesture each frame it moves, before the pages are drawn. */
@Composable
private fun <T : Any> Follows(
    back: PredictiveBack,
    gesture: NavigationEventState<SceneInfo<T>>,
    backStack: List<T>,
) {
    val state = gesture.transitionState
    SideEffect { back.follow(state, backStack) }
}

private fun <T : Any> ((T) -> NavEntry<T>).moving(
    backStack: List<T>,
    back: PredictiveBack,
    dark: Boolean,
): (T) -> NavEntry<T> = { key ->
    val entry = this(key)
    NavEntry(navEntry = entry) {
        // By identity: the same page can be open twice.
        val closed by remember(key) { derivedStateOf { backStack.none { it === key } } }
        Page(key, closed, back, dark) { entry.Content() }
    }
}

/**
 * [page], where [back] puts it during the gesture: the one revealed over the background, under the
 * scrim. Fades out once [closed] otherwise.
 */
@Composable
private fun Page(
    page: Any,
    closed: Boolean,
    back: PredictiveBack,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val fade by animateFloatAsState(if (closed) 0f else 1f, fadingOut(), label = "page alpha")
    val background = MaterialTheme.colorScheme.background
    Box(
        Modifier.drawWithContent {
            val revealed = back.reveals(page)
            if (revealed) drawRect(background)
            drawContent()
            if (revealed) drawRect(Color.Black, alpha = back.scrimAlpha(dark))
        },
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier.graphicsLayer { if (!with(back) { move(page) }) alpha = fade },
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * Sheets and dialogs over the whole app, as Flutter's `showModalBottomSheet` and `showDialog` were:
 * [show] puts one up and suspends until it closes, with what it closed with. Cancelling the caller
 * takes it down. [OverlayHost] shows them.
 */
@Stable
class Overlays {
    internal val shown = mutableStateListOf<@Composable () -> Unit>()

    /**
     * Shows [content] until it calls `close`, and returns what it was given: null when the user
     * dismissed it.
     */
    suspend fun <T : Any> show(content: @Composable (close: (T?) -> Unit) -> Unit): T? {
        val result = CompletableDeferred<T?>()
        val overlay: @Composable () -> Unit = { content { result.complete(it) } }
        shown += overlay
        try {
            return result.await()
        } finally {
            shown -= overlay
        }
    }
}

val LocalOverlays = staticCompositionLocalOf<Overlays> { error("No overlays provided") }

/** What [Overlays.show] puts up, above everything else under it. */
@Composable
fun OverlayHost(overlays: Overlays) {
    for (overlay in overlays.shown) key(overlay) { overlay() }
}

/**
 * A modal bottom sheet for [Overlays.show]: dismissed by the user it closes with null, and
 * `dismiss` slides it away before closing with a value.
 *
 * @param canDismiss whether the user can dismiss it now; not while it sends what they wrote, which
 *   it would cancel half-way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> OverlaySheet(
    close: (T?) -> Unit,
    canDismiss: Boolean = true,
    content: @Composable ColumnScope.(dismiss: (T?) -> Unit) -> Unit,
) {
    val dismissible by rememberUpdatedState(canDismiss)
    val state =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || dismissible },
        )
    val scope = rememberCoroutineScope()
    val dismiss: (T?) -> Unit = { value ->
        scope.launch { state.hide() }.invokeOnCompletion { close(value) }
    }
    ModalBottomSheet(
        // Back still asks to close when the sheet won't hide.
        onDismissRequest = { if (dismissible) close(null) },
        sheetState = state,
    ) {
        content(dismiss)
    }
}

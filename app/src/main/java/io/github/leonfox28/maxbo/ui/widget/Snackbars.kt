package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's one queue of snackbars, as Flutter's ScaffoldMessenger was: every page's scaffold shows
 * it, so a message outlives the page that sent it (say, "登录成功" after the login page closes).
 */
val LocalSnackbars = staticCompositionLocalOf<SnackbarHostState> { error("No snackbars provided") }

/** For a scaffold's `snackbarHost`. */
@Composable
fun AppSnackbarHost() {
    SnackbarHost(LocalSnackbars.current)
}

/**
 * Shows [message] in place of any snackbar showing, as Flutter's `hideCurrentSnackBar()` then
 * `showSnackBar()` did, without waiting for it to go. On [scope], which should outlive the page
 * when the page is closing, as after deleting the status it shows.
 */
fun SnackbarHostState.showMessage(message: String, scope: CoroutineScope) {
    currentSnackbarData?.dismiss()
    scope.launch { showSnackbar(message) }
}

package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

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

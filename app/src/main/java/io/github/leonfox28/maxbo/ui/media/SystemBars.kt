package io.github.leonfox28.maxbo.ui.media

import android.app.Activity
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Light status and navigation bar icons while this is shown, as over a black page. */
@Composable
fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window: Window? =
            (view.parent as? DialogWindowProvider)?.window ?: (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val light = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            light?.let { controller.isAppearanceLightStatusBars = it }
            lightNavigation?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

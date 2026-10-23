package io.github.leonfox28.maxbo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import io.github.leonfox28.maxbo.core.settings.TextSize

/** The text size the user chose, which `MaxboApp` applies to the page's `LocalDensity`. */
val LocalTextSize = compositionLocalOf { TextSize.Standard }

/**
 * Applies the chosen text size again in a dialog, sheet, menu or tooltip. Each of those is a window
 * of its own, whose `LocalDensity` starts over from the system's, so it would show text at the
 * system's size otherwise (Flutter's dialogs shared the app's tree, and its text size).
 */
@Composable
fun KeepTextSize(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDensity provides LocalTextSize.current.applyTo(LocalDensity.current),
        content = content,
    )
}

package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

/**
 * An app bar that folds away while [visible] is false (see [BarsOnScroll]). It sits above the list
 * it gives way to, never over it, so goes in a column with it, under the status bar's inset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HidingAppBar(
    visible: Boolean,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = { BackButton() },
    actions: @Composable RowScope.() -> Unit = {},
) {
    AnimatedVisibility(
        visible,
        enter = expandVertically(BarsOnScroll.spec(), expandFrom = Alignment.Bottom),
        exit = shrinkVertically(BarsOnScroll.spec(), shrinkTowards = Alignment.Bottom),
    ) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            windowInsets = WindowInsets(0),
        )
    }
}

package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity

/**
 * [body] under a [HidingAppBar] of [title] and [actions], which gives way while the user reads down
 * the body, as the home tab bar does. For a page without an app bar of its own.
 */
@Composable
fun HidingAppBarBody(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    body: @Composable () -> Unit,
) {
    val threshold = with(LocalDensity.current) { BarsOnScroll.Threshold.toPx() }
    val bars = remember(threshold) { BarsOnScroll(threshold) }
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    ) {
        HidingAppBar(bars.visible, title = title, actions = actions)
        Box(Modifier.weight(1f).nestedScroll(bars.connection)) { body() }
    }
}

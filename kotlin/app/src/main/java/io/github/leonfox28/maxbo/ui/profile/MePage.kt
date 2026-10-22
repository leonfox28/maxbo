package io.github.leonfox28.maxbo.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Route

// No app bar: the tab bar names the tab, and what an app bar would offer is
// listed here.
@Composable
fun MePage(padding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            ),
        contentPadding = padding,
    ) {
        item { AppLinks() }
    }
}

/** Settings, and diagnostics in debug builds. */
@Composable
private fun AppLinks() {
    val navigator = LocalNavigator.current
    ListItem(
        headlineContent = { Text("设置") },
        modifier = Modifier.clickable { navigator.push(Route.Settings) },
        leadingContent = { Icon(Icons.Outlined.Settings, contentDescription = null) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
    )
}

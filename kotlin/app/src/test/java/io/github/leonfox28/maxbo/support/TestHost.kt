package io.github.leonfox28.maxbo.support

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.media.LocalImageLoader
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.theme.MaxboTheme
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars

/** [content] with what the app provides around every page, as `MaxboApp` does. */
@Composable
fun TestHost(
    graph: AppGraph = remember { testGraph() },
    navigator: Navigator = remember { Navigator() },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppGraph provides graph,
        LocalNavigator provides navigator,
        LocalSnackbars provides remember { SnackbarHostState() },
        LocalImageLoader provides graph.imageLoader,
    ) {
        MaxboTheme(dark = false, content = content)
    }
}

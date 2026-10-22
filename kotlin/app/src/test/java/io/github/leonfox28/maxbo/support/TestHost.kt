package io.github.leonfox28.maxbo.support

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.media.LocalImageLoader
import io.github.leonfox28.maxbo.ui.compose.LocalPicturePicker
import io.github.leonfox28.maxbo.ui.compose.PicturePicker
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.theme.MaxboTheme
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.LocalOverlays
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.OverlayHost
import io.github.leonfox28.maxbo.ui.widget.Overlays

/** [content] with what the app provides around every page, as `MaxboApp` does. */
@Composable
fun TestHost(
    graph: AppGraph = remember { testGraph() },
    navigator: Navigator = remember { Navigator() },
    picturePicker: PicturePicker = PicturePicker { emptyList() },
    content: @Composable () -> Unit,
) {
    val overlays = remember { Overlays() }
    CompositionLocalProvider(
        LocalAppGraph provides graph,
        LocalNavigator provides navigator,
        LocalSnackbars provides remember { SnackbarHostState() },
        LocalOverlays provides overlays,
        LocalPicturePicker provides picturePicker,
        LocalImageLoader provides graph.imageLoader,
    ) {
        MaxboTheme(dark = false) {
            content()
            OverlayHost(overlays)
        }
    }
}

/** [content] with the app's snackbars showing over it, as a page's scaffold shows them. */
@Composable
fun WithSnackbars(content: @Composable () -> Unit) {
    Scaffold(snackbarHost = { AppSnackbarHost() }) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}

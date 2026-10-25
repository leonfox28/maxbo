package io.github.leonfox28.maxbo

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import io.github.leonfox28.maxbo.core.media.LocalImageLoader
import io.github.leonfox28.maxbo.ui.auth.LoginPage
import io.github.leonfox28.maxbo.ui.comments.CommentRepliesPage
import io.github.leonfox28.maxbo.ui.compose.ComposePage
import io.github.leonfox28.maxbo.ui.compose.LocalPicturePicker
import io.github.leonfox28.maxbo.ui.compose.PicturePicker
import io.github.leonfox28.maxbo.ui.compose.rememberGalleryPicker
import io.github.leonfox28.maxbo.ui.debug.DiagnosticsPage
import io.github.leonfox28.maxbo.ui.media.ImageViewerPage
import io.github.leonfox28.maxbo.ui.media.VideoPlayerPage
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.PageStack
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.notifications.UnreadChecks
import io.github.leonfox28.maxbo.ui.profile.ProfilePage
import io.github.leonfox28.maxbo.ui.search.SearchResultsPage
import io.github.leonfox28.maxbo.ui.settings.AboutPage
import io.github.leonfox28.maxbo.ui.settings.LicensesPage
import io.github.leonfox28.maxbo.ui.settings.SettingsPage
import io.github.leonfox28.maxbo.ui.settings.TextSizePage
import io.github.leonfox28.maxbo.ui.shell.HomeShell
import io.github.leonfox28.maxbo.ui.status.StatusDetailPage
import io.github.leonfox28.maxbo.ui.theme.KeepTextSize
import io.github.leonfox28.maxbo.ui.theme.LocalTextSize
import io.github.leonfox28.maxbo.ui.theme.MaxboTheme
import io.github.leonfox28.maxbo.ui.theme.isDark
import io.github.leonfox28.maxbo.ui.webview.WebPage
import io.github.leonfox28.maxbo.ui.widget.LocalOverlays
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.OverlayHost
import io.github.leonfox28.maxbo.ui.widget.Overlays

/**
 * The whole app, in the theme and text size the user has chosen.
 *
 * @param picturePicker tests swap in their own.
 */
@Composable
fun MaxboApp(
    graph: AppGraph,
    navigator: Navigator = remember { Navigator() },
    picturePicker: PicturePicker = rememberGalleryPicker(),
) {
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val dark = settings.themeMode.isDark()
    val overlays = remember { Overlays() }
    SystemBarsFor(dark)
    CompositionLocalProvider(
        LocalAppGraph provides graph,
        LocalNavigator provides navigator,
        LocalSnackbars provides remember { SnackbarHostState() },
        LocalOverlays provides overlays,
        LocalPicturePicker provides picturePicker,
        LocalImageLoader provides graph.imageLoader,
        // On top of the system's font size, for every page and snackbar;
        // dialogs, sheets and menus apply it again (KeepTextSize).
        LocalDensity provides settings.textSize.applyTo(LocalDensity.current),
        LocalTextSize provides settings.textSize,
    ) {
        // Out here, where it stays for as long as the app: under a page pushed
        // over the tabs, they leave the composition.
        UnreadChecks()
        MaxboTheme(dark = dark) {
            PageStack(
                backStack = navigator.backStack,
                onBack = navigator::pop,
                dark = dark,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                // The image viewer is a dialog over the page it opened from.
                sceneStrategies = listOf(DialogSceneStrategy(), SinglePaneSceneStrategy()),
                entryProvider = pages,
            )
            OverlayHost(overlays)
        }
    }
}

private val FullScreenDialog =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

/** What each page shows. */
private val pages: (Route) -> NavEntry<Route> = entryProvider {
    entry<Route.Home> { HomeShell() }
    entry<Route.Login> { LoginPage() }
    entry<Route.StatusDetail> { StatusDetailPage(it.id, it.initial) }
    entry<Route.Comments> { CommentRepliesPage(it.thread) }
    entry<Route.Viewer>(metadata = DialogSceneStrategy.dialog(FullScreenDialog)) {
        KeepTextSize { ImageViewerPage(it.pics, it.index, it.statusId) }
    }
    entry<Route.Video> { VideoPlayerPage(it.id, it.index, it.initial) }
    entry<Route.Profile> { ProfilePage(uid = it.uid, initial = it.initial) }
    entry<Route.ProfileByName> { ProfilePage(screenName = it.name) }
    entry<Route.SearchResults> { SearchResultsPage(it.query) }
    entry<Route.Web> { WebPage(it.url) }
    entry<Route.Settings> { SettingsPage() }
    entry<Route.TextSize> { TextSizePage() }
    entry<Route.About> { AboutPage() }
    entry<Route.Licenses> { LicensesPage() }
    entry<Route.Compose> { ComposePage() }
    entry<Route.Diagnostics> { if (BuildConfig.DEBUG) DiagnosticsPage() }
}

/**
 * Edge to edge, with status bar icons to suit the theme where no app bar sets them, as on the tabs
 * or once a page's app bar has scrolled away.
 */
@Composable
private fun SystemBarsFor(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(activity, dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { dark },
        )
        onDispose {}
    }
}

// The scrims enableEdgeToEdge() puts behind three-button navigation.
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

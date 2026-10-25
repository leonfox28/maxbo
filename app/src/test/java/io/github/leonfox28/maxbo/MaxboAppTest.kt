package io.github.leonfox28.maxbo

import android.app.UiModeManager
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.leonfox28.maxbo.core.settings.AppSettings
import io.github.leonfox28.maxbo.core.settings.ThemeMode
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.ui.theme.DarkColors
import io.github.leonfox28.maxbo.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class MaxboAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // The system is light.
    private val graph = testGraph(settings = AppSettings(themeMode = ThemeMode.Dark))

    private fun start() {
        compose.setContent { MaxboApp(graph) }
        compose.waitForIdle()
    }

    private fun choose(mode: ThemeMode) {
        graph.settings.update { it.copy(themeMode = mode) }
        compose.waitForIdle()
    }

    private fun windowColor() =
        (compose.activity.window.decorView.background as ColorDrawable).color

    private fun nightMode() =
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java)).applicationNightMode

    @Test
    @Config(sdk = [24, 34])
    fun `paints the window in the app's theme, not the system's`() {
        start()
        assertEquals(DarkColors.background.toArgb(), windowColor())

        choose(ThemeMode.Light)
        assertEquals(LightColors.background.toArgb(), windowColor())
    }

    @Test
    fun `tells the system the theme, for the window it starts the app in`() {
        start()
        assertEquals(UiModeManager.MODE_NIGHT_YES, nightMode())

        choose(ThemeMode.Light)
        assertEquals(UiModeManager.MODE_NIGHT_NO, nightMode())

        choose(ThemeMode.System)
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, nightMode())
    }
}

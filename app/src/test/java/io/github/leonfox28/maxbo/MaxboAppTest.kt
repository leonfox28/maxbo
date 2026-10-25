package io.github.leonfox28.maxbo

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

@RunWith(RobolectricTestRunner::class)
class MaxboAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun windowColor() =
        (compose.activity.window.decorView.background as ColorDrawable).color

    @Test
    fun `paints the window in the app's theme, not the system's`() {
        // The system is light.
        val graph = testGraph(settings = AppSettings(themeMode = ThemeMode.Dark))
        compose.setContent { MaxboApp(graph) }
        compose.waitForIdle()
        assertEquals(DarkColors.background.toArgb(), windowColor())

        graph.settings.update { it.copy(themeMode = ThemeMode.Light) }
        compose.waitForIdle()
        assertEquals(LightColors.background.toArgb(), windowColor())
    }
}

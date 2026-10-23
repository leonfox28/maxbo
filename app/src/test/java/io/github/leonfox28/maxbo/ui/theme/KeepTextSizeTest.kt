package io.github.leonfox28.maxbo.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import io.github.leonfox28.maxbo.core.settings.TextSize
import io.github.leonfox28.maxbo.ui.widget.AppAlertDialog
import io.github.leonfox28.maxbo.ui.widget.OverlaySheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeepTextSizeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `dialogs and sheets keep the chosen size, in windows of their own`() {
        val seen = mutableMapOf<String, Float>()
        compose.setContent {
            // As MaxboApp provides them.
            CompositionLocalProvider(
                LocalDensity provides TextSize.ExtraLarge.applyTo(LocalDensity.current),
                LocalTextSize provides TextSize.ExtraLarge,
            ) {
                seen["page"] = LocalDensity.current.fontScale
                AppAlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    text = { seen["dialog"] = LocalDensity.current.fontScale },
                )
                OverlaySheet<Unit>(close = {}) { seen["sheet"] = LocalDensity.current.fontScale }
            }
        }
        compose.waitForIdle()

        assertEquals(1.3f, seen.getValue("page"), 1e-6f)
        assertEquals(1.3f, seen.getValue("dialog"), 1e-6f)
        assertEquals(1.3f, seen.getValue("sheet"), 1e-6f)
    }

    @Test
    fun `a size applied again scales from the system's, not twice`() {
        val system = Density(density = 2f, fontScale = 1.1f)

        val once = TextSize.Large.applyTo(system)
        val twice = TextSize.Large.applyTo(once)

        assertEquals(once.fontScale, twice.fontScale, 1e-6f)
        assertEquals(1.1f, TextSize.Standard.applyTo(once).fontScale, 1e-6f)
    }
}

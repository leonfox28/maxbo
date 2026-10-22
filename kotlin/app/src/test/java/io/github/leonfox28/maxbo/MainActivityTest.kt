package io.github.leonfox28.maxbo

import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    private fun launched(check: (Configuration) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { check(it.resources.configuration) }
        }
    }

    @Test
    @Config(sdk = [24, 34], qualifiers = "en-rUS")
    fun `speaks Chinese whatever the system language`() {
        launched { assertEquals(Locale.SIMPLIFIED_CHINESE, it.locales[0]) }
    }

    @Test
    @Config(sdk = [24, 34])
    fun `keeps the system's font size`() {
        RuntimeEnvironment.setFontScale(1.3f)
        launched { assertEquals(1.3f, it.fontScale) }
    }

    @Test
    @Config(sdk = [34])
    fun `follows the system into dark mode`() {
        launched {
            assertEquals(
                Configuration.UI_MODE_NIGHT_NO,
                it.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            )
        }
        RuntimeEnvironment.setQualifiers("+night")
        launched {
            assertEquals(
                Configuration.UI_MODE_NIGHT_YES,
                it.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            )
        }
    }
}

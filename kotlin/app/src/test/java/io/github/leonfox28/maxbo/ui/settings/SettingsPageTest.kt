package io.github.leonfox28.maxbo.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.leonfox28.maxbo.MB
import io.github.leonfox28.maxbo.core.settings.ImageQuality
import io.github.leonfox28.maxbo.core.settings.ThemeMode
import io.github.leonfox28.maxbo.support.FakePictureCache
import io.github.leonfox28.maxbo.support.InMemorySettingsStorage
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsPageTest {
    @get:Rule val compose = createComposeRule()

    private val storage = InMemorySettingsStorage()
    private val cache = FakePictureCache(450 * MB)
    private val graph = testGraph(settingsStorage = storage, pictureCache = cache)

    @Before
    fun showPage() {
        compose.setContent { TestHost(graph) { SettingsPage() } }
        waitFor("已用 450 MB")
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `shows the current settings and cache size`() {
        compose.onNodeWithText("跟随系统").assertExists()
        // The text size and the image quality.
        compose.onAllNodesWithText("标准").assertCountEquals(2)
        compose.onNodeWithText("300 MB，超出后先删最早下载的图片").assertExists()
    }

    @Test
    fun `picks a theme and saves it`() {
        tap("主题")
        tap("深色")

        assertEquals(ThemeMode.Dark, storage.stored.themeMode)
        // Shown once: the dialog has gone.
        compose.onAllNodesWithText("深色").assertCountEquals(1)
    }

    @Test
    fun `picks an image quality`() {
        tap("图片质量")
        compose.onNodeWithText(ImageQuality.Original.description).assertExists()
        tap("省流量")

        assertEquals(ImageQuality.DataSaver, storage.stored.imageQuality)
    }

    @Test
    fun `lowering the cache limit trims the cache`() {
        tap("图片缓存上限")
        tap("100 MB")

        assertEquals(100, storage.stored.imageCacheLimitMb)
        assertEquals(listOf(100 * MB), cache.limits)
        waitFor("已用 100 MB")
    }

    @Test
    fun `clears the cache after confirming`() {
        tap("清除图片缓存")
        tap("取消")
        assertEquals(0, cache.clears)

        tap("清除图片缓存")
        tap("清除")

        assertEquals(1, cache.clears)
        waitFor("已用 0 MB")
        waitFor("图片缓存已清除")
    }
}

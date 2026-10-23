package io.github.leonfox28.maxbo.core.settings

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.support.InMemorySettingsStorage
import io.github.leonfox28.maxbo.support.picJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    private val pic = PicInfo.tryFromJson(picJson("p", width = 3000, height = 2000))!!

    @Test
    fun `image quality saves data in lists by ignoring the pixel ratio`() {
        assertEquals(360f, ImageQuality.Standard.listPixels(120f, 3f))
        assertEquals(360f, ImageQuality.Original.listPixels(120f, 3f))
        assertEquals(120f, ImageQuality.DataSaver.listPixels(120f, 3f))
        assertTrue(
            pic.variantFor(ImageQuality.DataSaver.listPixels(120f, 3f)).url.contains("/wap180/")
        )
    }

    @Test
    fun `image quality shows the original in the viewer only when asked to`() {
        assertTrue(ImageQuality.Standard.viewerVariant(pic).url.contains("/mw2000/"))
        assertTrue(ImageQuality.DataSaver.viewerVariant(pic).url.contains("/mw2000/"))
        assertTrue(ImageQuality.Original.viewerVariant(pic).url.contains("/large/"))
    }

    @Test
    fun `text size scales text on top of the device setting`() {
        val device = Density(density = 1f, fontScale = 2f)
        assertSame(device, TextSize.Standard.applyTo(device))
        assertEquals(23f, with(TextSize.Large.applyTo(device)) { 10.sp.toDp().value }, 1e-4f)
        assertEquals(18f, with(TextSize.Small.applyTo(device)) { 10.sp.toDp().value }, 1e-4f)
        assertEquals(
            13f,
            with(TextSize.ExtraLarge.applyTo(Density(1f))) { 10.sp.toDp().value },
            1e-4f,
        )
    }

    @Test
    fun `text size leaves everything else its size`() {
        val scaled = TextSize.Large.applyTo(Density(density = 3f, fontScale = 2f))
        assertEquals(3f, scaled.density)
        assertEquals(2.3f, scaled.fontScale, 1e-4f)
        assertEquals(30f, with(scaled) { 10.dp.toPx() })
        assertEquals(10f, with(scaled) { 23.dp.toSp().value }, 1e-4f)
    }

    @Test
    fun `equal sizes give equal densities, sparing recompositions`() {
        val device = Density(1f)
        assertEquals(TextSize.Large.applyTo(device), TextSize.Large.applyTo(device))
        assertNotEquals(TextSize.Large.applyTo(device), TextSize.ExtraLarge.applyTo(device))
    }

    @Test
    fun `starts from the loaded settings and saves every change`() = runTest {
        val storage = InMemorySettingsStorage()
        val controller =
            SettingsController(AppSettings(themeMode = ThemeMode.Dark), storage, backgroundScope)

        assertEquals(ThemeMode.Dark, controller.settings.value.themeMode)
        controller.update { it.copy(imageQuality = ImageQuality.DataSaver) }.join()

        val settings = controller.settings.value
        assertEquals(ThemeMode.Dark, settings.themeMode)
        assertEquals(ImageQuality.DataSaver, settings.imageQuality)
        assertEquals(1, storage.saves)
        assertEquals(ImageQuality.DataSaver, storage.stored.imageQuality)
    }

    @Test
    fun `quick changes all end up saved`() = runTest {
        val storage = InMemorySettingsStorage()
        val controller = SettingsController(AppSettings(), storage, backgroundScope)

        controller.update { it.copy(textSize = TextSize.Large) }
        controller.update { it.copy(themeMode = ThemeMode.Light) }.join()

        assertEquals(
            AppSettings(themeMode = ThemeMode.Light, textSize = TextSize.Large),
            storage.stored,
        )
    }
}

package io.github.leonfox28.maxbo.core.settings

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.data.model.PicVariant

/** Light, dark, or as the system has it. */
enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}

/** Which picture renditions to load. */
enum class ImageQuality(val key: String, val label: String, val description: String) {
    DataSaver("dataSaver", "省流量", "列表里的图片用较小的尺寸"),
    Standard("standard", "标准", "按屏幕清晰度选择尺寸"),
    Original("original", "原图", "查看大图时加载原图，可能有好几 MB");

    /**
     * The physical pixels an in-list picture [logical] dp across should cover, on a screen of
     * [density] pixels per dp.
     */
    fun listPixels(logical: Float, density: Float): Float =
        logical * (if (this == DataSaver) 1f else density)

    /** The rendition the image viewer shows. */
    fun viewerVariant(pic: PicInfo): PicVariant =
        if (this == Original) pic.largest ?: pic.viewerVariant else pic.viewerVariant
}

/**
 * How large text is, relative to the font size set on the device: the standard size, or up to two
 * steps either way.
 */
enum class TextSize(val key: String, val label: String, val factor: Float) {
    ExtraSmall("extraSmall", "特小", 0.8f),
    Small("small", "小", 0.9f),
    Standard("standard", "标准", 1f),
    Large("large", "大", 1.15f),
    ExtraLarge("extraLarge", "特大", 1.3f);

    /** The device's text scaling, [system], enlarged or reduced to this size. */
    fun applyTo(system: Density): Density =
        if (factor == 1f) system else ScaledTextDensity(system, factor)
}

/**
 * Scales text as [base] does, then by [factor], keeping [base]'s nonlinear scaling (Android 14 and
 * later), if any. Everything other than text keeps [base]'s size.
 */
private data class ScaledTextDensity(val base: Density, val factor: Float) : Density {
    override val density: Float
        get() = base.density

    override val fontScale: Float
        get() = base.fontScale * factor

    override fun TextUnit.toDp(): Dp = with(base) { this@toDp.toDp() } * factor

    override fun Dp.toSp(): TextUnit = with(base) { (this@toSp / factor).toSp() }
}

/** User preferences, kept on the device only. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val textSize: TextSize = TextSize.Standard,
    val imageQuality: ImageQuality = ImageQuality.Standard,
    /** How large the picture disk cache may grow before the oldest pictures are deleted. */
    val imageCacheLimitMb: Int = 300,
) {
    companion object {
        val imageCacheLimitChoices = listOf(100, 300, 500, 1024)
    }
}

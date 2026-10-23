package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.readInt
import io.github.leonfox28.maxbo.core.util.readMap
import io.github.leonfox28.maxbo.core.util.readString
import io.github.leonfox28.maxbo.core.util.readUrl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class PicType {
    Pic,
    Gif,
    LivePhoto,
}

/** One rendition of a picture. */
data class PicVariant(val url: String, val width: Int, val height: Int)

/** A picture attached to a status, in the renditions Weibo offers. */
data class PicInfo(
    val id: String,
    val type: PicType,
    /** Renditions suitable for in-list display, smallest first. */
    val variants: List<PicVariant>,
    /** The full-size picture, which may be many megabytes; only for viewing. */
    val largest: PicVariant? = null,
) {
    /** Width over height of the picture, if known. */
    val aspectRatio: Float?
        get() {
            val v = largest ?: variants.last()
            return if (v.width > 0 && v.height > 0) v.width.toFloat() / v.height else null
        }

    /** Much taller than wide; shown cropped with a badge. */
    val isLong: Boolean
        get() = (aspectRatio ?: 1f) < 1f / 3

    /**
     * The smallest rendition whose shorter side covers [pixels], or the largest available. A
     * rendition of unknown size is taken to cover it.
     */
    fun variantFor(pixels: Float): PicVariant {
        for (v in variants) {
            val shorter = minOf(v.width, v.height)
            if (shorter >= pixels || shorter == 0) return v
        }
        return variants.last()
    }

    /**
     * What the image viewer shows: the largest list rendition (up to about 2000 px wide, enough for
     * a phone screen), or for a GIF the full file, as smaller renditions may not be animated.
     */
    val viewerVariant: PicVariant
        get() = if (type == PicType.Gif) largest ?: variants.last() else variants.last()

    companion object {
        // Names don't reliably say how big a rendition is (`original` is
        // often a mid-size crop), so variants are chosen by their actual size
        // instead.
        private val listVariants = listOf("thumbnail", "bmiddle", "large", "mw2000")
        private val fullVariants = listOf("largest", "original", "woriginal")

        /** A picture known only by its URL, such as one linked from a status body. */
        fun fromUrl(url: String) =
            PicInfo(
                id = url,
                type =
                    if (url.toHttpUrlOrNull()?.encodedPath?.endsWith(".gif") == true) PicType.Gif
                    else PicType.Pic,
                variants = listOf(PicVariant(url, 0, 0)),
            )

        /**
         * [id] names the picture when the JSON itself doesn't, as in comment pictures, which are
         * keyed by id.
         */
        fun tryFromJson(json: JsonElement?, id: String? = null): PicInfo? {
            val map = readMap(json) ?: return null
            val picId = readString(map["pic_id"]) ?: readString(map["object_id"]) ?: id
            var variants = listVariants.mapNotNull { variant(map[it]) }
            // Without sizes (comment pictures), keep the smallest-first key order.
            if (variants.all { it.width > 0 }) variants = variants.sortedBy { it.width }
            if (picId == null || variants.isEmpty()) return null
            return PicInfo(
                id = picId,
                type =
                    when (readString(map["type"])) {
                        "gif" -> PicType.Gif
                        "livephoto" -> PicType.LivePhoto
                        else -> PicType.Pic
                    },
                variants = variants,
                largest =
                    fullVariants
                        .mapNotNull { variant(map[it]) }
                        .fold(null as PicVariant?) { best, v ->
                            if (best == null || v.width > best.width) v else best
                        },
            )
        }

        /**
         * A picture known only by its [pid], such as one on a comment just sent, in the renditions
         * comment pictures come in, at the address weibo.com gives it; null for an older kind of
         * pid.
         */
        fun tryFromPid(pid: String): PicInfo? {
            if (pid.length < 32 || pid[9] != 'y') return null
            val host = "https://wx${(crc32(pid) and 3) + 1}.sinaimg.cn"
            val type = if (pid[21] == 'g') PicType.Gif else PicType.Pic
            val file = "$pid.${if (type == PicType.Gif) "gif" else "jpg"}"
            fun variant(name: String) = PicVariant("$host/$name/$file", 0, 0)
            return PicInfo(
                id = pid,
                type = type,
                variants = listVariants.take(3).map(::variant),
                largest = variant("woriginal"),
            )
        }

        private fun crc32(s: String): Long {
            val crc = java.util.zip.CRC32()
            crc.update(s.toByteArray(Charsets.ISO_8859_1))
            return crc.value
        }

        private fun variant(json: JsonElement?): PicVariant? {
            val map = readMap(json) ?: return null
            val url = readUrl(map["url"]) ?: return null
            return PicVariant(
                url,
                readInt(map["width"])?.toInt() ?: 0,
                readInt(map["height"])?.toInt() ?: 0,
            )
        }
    }
}

/**
 * The pictures of a JSON object holding `pic_infos` keyed by id, in the display order of its
 * `pic_ids`.
 */
fun picsOf(json: JsonObject): List<PicInfo> {
    val infos = readMap(json["pic_infos"]) ?: return emptyList()
    val order = (json["pic_ids"] as? JsonArray)?.mapNotNull { readString(it) }.orEmpty()
    return (order.ifEmpty { infos.keys.toList() }).mapNotNull {
        PicInfo.tryFromJson(infos[it], id = it)
    }
}

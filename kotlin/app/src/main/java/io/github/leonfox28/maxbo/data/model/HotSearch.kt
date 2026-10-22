package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.readInt
import io.github.leonfox28.maxbo.core.util.readMap
import io.github.leonfox28.maxbo.core.util.readMaps
import io.github.leonfox28.maxbo.core.util.readString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Weibo's hot search list ("热搜榜"), as `side/hotSearch` sends it. */
data class HotSearch(
    /** Entries pinned above the ranking. */
    val pinned: List<HotSearchItem> = emptyList(),
    /**
     * The ranking, with the entries Weibo inserts into it (including paid ones) where Weibo puts
     * them. Nothing is filtered out (PLAN §2).
     */
    val items: List<HotSearchItem> = emptyList(),
) {
    companion object {
        fun fromJson(data: JsonObject): HotSearch {
            // Both are sent: `hotgovs` lists the pinned entries, `hotgov`
            // holds one (with a few more fields).
            val govs = readMaps(data["hotgovs"])
            val pinned = govs.ifEmpty { listOfNotNull(readMap(data["hotgov"])) }
            return HotSearch(
                pinned = pinned.mapNotNull(HotSearchItem::tryFromJson),
                items = readMaps(data["realtime"]).mapNotNull(HotSearchItem::tryFromJson),
            )
        }
    }
}

data class HotSearchItem(
    /** The entry as shown, without the `#` of topics. */
    val word: String,
    /** What to search for: the word, or `#word#` for a topic. */
    val query: String,
    /** The position in the ranking, from 1; null for entries outside it. */
    val rank: Int? = null,
    /** Weibo's heat index. */
    val heat: Long? = null,
    /** Weibo's label, such as 热, 新, or 商 for a paid entry. */
    val badge: String? = null,
    /** The label colour as 0xAARRGGBB. */
    val badgeColor: Long? = null,
    /**
     * Marked `is_ad` by Weibo. This covers more than paid entries (a rumour rebuttal had it too),
     * so it isn't shown as an ad label by itself.
     */
    val isAd: Boolean = false,
) {
    companion object {
        fun tryFromJson(json: JsonObject): HotSearchItem? {
            val raw =
                readString(json["word"])
                    ?: readString(json["note"])
                    ?: readString(json["name"])
                    ?: return null
            val word =
                if (raw.length > 2 && raw.startsWith("#") && raw.endsWith("#"))
                    raw.substring(1, raw.length - 1)
                else raw
            val isAd = readInt(json["is_ad"]) == 1L
            // The short label is the one that says 商 on paid entries.
            val short = readString(json["small_icon_desc"])
            val badge = short ?: readString(json["icon_desc"])
            val color =
                if (short != null) json["small_icon_desc_color"] else json["icon_desc_color"]
            return HotSearchItem(
                word = word,
                query = readString(json["word_scheme"]) ?: raw,
                rank = readInt(json["realpos"])?.toInt(),
                heat = readInt(json["num"]),
                // Never leave a promoted entry unmarked.
                badge = badge ?: if (isAd) "荐" else null,
                badgeColor = color(color),
                isAd = isAd,
            )
        }

        /** Parses `#rrggbb`. */
        private fun color(value: JsonElement?): Long? {
            val hex = readString(value) ?: return null
            if (!Regex("^#[0-9a-fA-F]{6}$").matches(hex)) return null
            return 0xFF000000L or hex.substring(1).toLong(16)
        }
    }
}

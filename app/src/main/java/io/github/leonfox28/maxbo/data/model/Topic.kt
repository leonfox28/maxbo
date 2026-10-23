package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.readInt
import io.github.leonfox28.maxbo.core.util.readMap
import io.github.leonfox28.maxbo.core.util.readString
import io.github.leonfox28.maxbo.core.util.readUrl
import kotlinx.serialization.json.JsonElement

/** A `#话题#` as a search for it describes it ("导语", how many read it). */
data class Topic(
    /** The topic without its `#`. */
    val name: String,
    /** Its lead ("导语"). */
    val summary: String? = null,
    val imageUrl: String? = null,
    /** How many times it was read ("阅读"), all time. */
    val reads: Long? = null,
    /** How many posts discuss it ("讨论"). */
    val mentions: Long? = null,
    /** How many people posted in it ("原创人数"). */
    val authors: Long? = null,
) {
    companion object {
        /**
         * Reads the `topicHeads` of `statuses/search`. Searching a plain word gets one too, with
         * counts but no name: null then, as it is no topic.
         */
        fun tryFromJson(json: JsonElement?): Topic? {
            val map = readMap(json) ?: return null
            val obj = readMap(map["object"])
            val name = readString(obj?.get("display_name")) ?: return null
            val counts = readMap(map["count"])
            return Topic(
                name = name,
                summary = readString(obj?.get("summary")),
                imageUrl = readUrl(readMap(obj?.get("image"))?.get("url")),
                reads = readInt(counts?.get("read")),
                mentions = readInt(counts?.get("mention")),
                authors = readInt(counts?.get("ori_uv")),
            )
        }
    }
}

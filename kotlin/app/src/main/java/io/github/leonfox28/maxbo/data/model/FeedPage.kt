package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.readMaps
import io.github.leonfox28.maxbo.core.util.readString
import kotlinx.serialization.json.JsonObject

/** One page of a timeline. */
data class FeedPage(
    val statuses: List<Status>,
    /** The `max_id` that fetches the next, older page; null at the end. */
    val nextCursor: String? = null,
) {
    companion object {
        fun fromJson(json: JsonObject): FeedPage {
            val statuses = readMaps(json["statuses"]).mapNotNull { Status.tryFromJson(it) }
            // The `_str` twins carry the same ids without risking number
            // precision.
            val maxId = readString(json["max_id_str"]) ?: readString(json["max_id"])
            return FeedPage(
                statuses = statuses,
                nextCursor =
                    if (statuses.isEmpty() || maxId == null || maxId == "0") null else maxId,
            )
        }
    }
}

/** One page of a list that continues from a cursor. */
data class Paged<T>(
    val items: List<T>,
    /** Fetches the page after this one; null at the end. */
    val nextCursor: String? = null,
    /** How many items the whole list has, when Weibo says. */
    val total: Int? = null,
)

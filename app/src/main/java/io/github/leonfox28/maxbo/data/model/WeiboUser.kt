package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.readBool
import io.github.leonfox28.maxbo.core.util.readInt
import io.github.leonfox28.maxbo.core.util.readMap
import io.github.leonfox28.maxbo.core.util.readString
import io.github.leonfox28.maxbo.core.util.readUrl
import kotlinx.serialization.json.JsonElement

/** A Weibo user as embedded in statuses. */
data class WeiboUser(
    val id: String,
    val screenName: String,
    /** A signed URL that expires after a few hours; see `imageCacheKey`. */
    val avatarUrl: String? = null,
    /**
     * Weibo's verification kind when verified: 0 is a person, 1–7 are organisations; null when
     * unverified.
     */
    val verifiedType: Int? = null,
) {
    companion object {
        fun tryFromJson(json: JsonElement?): WeiboUser? {
            val map = readMap(json) ?: return null
            val id = readString(map["idstr"]) ?: readString(map["id"]) ?: return null
            val name = readString(map["screen_name"]) ?: return null
            return WeiboUser(
                id = id,
                screenName = name,
                avatarUrl =
                    readUrl(map["avatar_large"])
                        ?: readUrl(map["profile_image_url"])
                        ?: readUrl(map["avatar_hd"]),
                verifiedType =
                    if (readBool(map["verified"]) == true) readInt(map["verified_type"])?.toInt()
                    else null,
            )
        }
    }
}

package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.readBool
import io.github.leonfox28.maxbo.core.util.readInt
import io.github.leonfox28.maxbo.core.util.readMap
import io.github.leonfox28.maxbo.core.util.readString
import io.github.leonfox28.maxbo.core.util.readUrl
import kotlinx.serialization.json.JsonElement

/** A user as their profile page shows them. */
data class UserProfile(
    val user: WeiboUser,
    val description: String? = null,
    val location: String? = null,
    /** `m` or `f`. */
    val gender: String? = null,
    /** What Weibo verified the user as, e.g. a job title. */
    val verifiedReason: String? = null,
    val coverUrl: String? = null,
    val followersCount: Long? = null,
    val friendsCount: Long? = null,
    val statusesCount: Long? = null,
    /** Whether the signed-in user follows this user. */
    val following: Boolean = false,
    /** Whether this user follows the signed-in user. */
    val followsMe: Boolean = false,
) {
    companion object {
        /** Reads the `data` object of `profile/info`. */
        fun tryFromJson(json: JsonElement?): UserProfile? {
            val map = readMap(readMap(json)?.get("user")) ?: return null
            val user = WeiboUser.tryFromJson(map) ?: return null
            return UserProfile(
                user = user,
                description = trimmed(map["description"]),
                location = trimmed(map["location"]),
                gender = readString(map["gender"]),
                verifiedReason =
                    if (user.verifiedType == null) null else trimmed(map["verified_reason"]),
                // Sometimes several URLs joined with `;`.
                coverUrl = readUrl(readString(map["cover_image_phone"])?.split(';')?.first()),
                followersCount = readInt(map["followers_count"]),
                friendsCount = readInt(map["friends_count"]),
                statusesCount = readInt(map["statuses_count"]),
                following = readBool(map["following"]) == true,
                followsMe = readBool(map["follow_me"]) == true,
            )
        }

        private fun trimmed(value: JsonElement?) = readString(value)?.trim()?.ifEmpty { null }
    }
}

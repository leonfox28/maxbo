package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.readString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The signed-in Weibo account. */
data class Account(val uid: String, val screenName: String? = null) {
    fun toJson(): JsonObject = buildJsonObject {
        put("uid", uid)
        put("screenName", screenName)
    }

    companion object {
        /**
         * Reads the account from the `$CONFIG` object weibo.com embeds in its pages, or returns
         * null if it names no signed-in user (as for visitors).
         */
        fun tryFromPageConfig(config: JsonObject): Account? {
            val user = config["user"] as? JsonObject
            val uid =
                readString(config["uid"])
                    ?: user?.let { readString(it["idstr"]) ?: readString(it["id"]) }
            if (uid == null || uid == "0") return null
            return Account(uid = uid, screenName = user?.let { readString(it["screen_name"]) })
        }

        fun fromJson(json: JsonObject) =
            Account(
                uid = json.getValue("uid").jsonPrimitive.content,
                screenName = (json["screenName"] as? JsonPrimitive)?.contentOrNull,
            )
    }
}

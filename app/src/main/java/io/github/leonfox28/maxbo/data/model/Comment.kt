package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.parseWeiboDate
import io.github.leonfox28.maxbo.core.util.readBool
import io.github.leonfox28.maxbo.core.util.readInt
import io.github.leonfox28.maxbo.core.util.readMap
import io.github.leonfox28.maxbo.core.util.readMaps
import io.github.leonfox28.maxbo.core.util.readString
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** How a status's comments are sorted. */
enum class CommentOrder {
    Hot,
    Time,
}

/** A comment on a status, or a reply in a comment's thread ("楼中楼"). */
data class Comment(
    val id: String,
    val user: WeiboUser? = null,
    val createdAt: Instant? = null,
    /** Where it was posted from, e.g. `来自上海`. */
    val source: String? = null,
    /**
     * The body as HTML, like `Status.text`. Replies in a thread start with `回复@name:` themselves.
     */
    val text: String = "",
    val urls: List<UrlEntity> = emptyList(),
    /** Attached pictures, which [text] doesn't mention. */
    val pics: List<PicInfo> = emptyList(),
    val likesCount: Int = 0,
    val liked: Boolean = false,
    /** How many replies its thread has; [replies] previews the first few. */
    val replyCount: Int = 0,
    val replies: List<Comment> = emptyList(),
    /** False when its author turned replies off. */
    val canReply: Boolean = true,
) {
    companion object {
        fun tryFromJson(json: JsonElement?): Comment? {
            val map = readMap(json) ?: return null
            val id = readString(map["idstr"]) ?: readString(map["id"]) ?: return null
            val urlStruct = readMaps(map["url_struct"])
            val pics = ownPics(map, urlStruct)
            var text = readString(map["text"]).orEmpty()
            // A comment just sent names its pictures only in pic_ids, and its
            // text ends with a short link to each, which url_struct would
            // otherwise have hidden.
            if (pics.isNotEmpty() && urlStruct.none { it.hasPictures() }) {
                repeat(pics.size) { text = text.replaceFirst(trailingShortLink, "") }
            }
            return Comment(
                id = id,
                user = WeiboUser.tryFromJson(map["user"]),
                createdAt = parseWeiboDate(readString(map["created_at"])),
                source = readString(map["source"])?.trim(),
                text = text,
                urls = urlStruct.mapNotNull(UrlEntity::tryFromJson),
                pics = pics,
                likesCount = readInt(map["like_counts"])?.toInt() ?: 0,
                liked = readBool(map["liked"]) == true,
                replyCount = readInt(map["total_number"])?.toInt() ?: 0,
                replies = readMaps(map["comments"]).mapNotNull { tryFromJson(it) },
                canReply = readInt(map["disable_reply"]) != 1L,
            )
        }

        private val trailingShortLink =
            Regex(
                """\s*<a\s[^>]*href="?https?://t\.cn/[^>]*>(?:(?!</a>).)*</a>\s*$""",
                RegexOption.DOT_MATCHES_ALL,
            )

        private fun JsonObject.hasPictures() =
            this["pic_infos"].let { it != null && it !is JsonNull }

        // Pictures come as url_struct entries, each with its own pic_infos. A
        // reply also carries the entry of a picture on the comment it
        // answers, but has pic_num 0 then; its own pictures are linked from
        // its text_raw. A comment just sent has no url_struct, only the pids
        // in pic_ids.
        private fun ownPics(map: JsonObject, urlStruct: List<JsonObject>): List<PicInfo> {
            if (readInt(map["pic_num"]) == 0L) return emptyList()
            val entries = urlStruct.filter { it.hasPictures() }
            if (entries.isEmpty()) {
                return (map["pic_ids"] as? JsonArray)
                    ?.mapNotNull { readString(it) }
                    ?.mapNotNull(PicInfo::tryFromPid)
                    .orEmpty()
            }
            val raw = readString(map["text_raw"])
            val linked = entries.filter { entry ->
                val short = readString(entry["short_url"])
                short != null && raw?.contains(short) == true
            }
            return linked.ifEmpty { entries }.flatMap(::picsOf)
        }
    }
}

/** [comment] with [reply] (if Weibo returned it) added to its thread. */
fun Comment.withReply(reply: Comment?): Comment =
    copy(
        replyCount = replyCount + 1,
        // The preview shows the first two replies.
        replies = if (reply != null && replies.size < 2) replies + reply else replies,
    )

/** [comment] with reply [id] deleted from its thread. */
fun Comment.withoutReply(id: String): Comment =
    copy(replyCount = maxOf(0, replyCount - 1), replies = replies.filter { it.id != id })

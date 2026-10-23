package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.parseWeiboDate
import io.github.leonfox28.maxbo.core.util.readInt
import io.github.leonfox28.maxbo.core.util.readMap
import io.github.leonfox28.maxbo.core.util.readString
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The lists of weibo.com's 消息 page that maxbo shows. */
enum class NoticeKind {
    /** Comments on the user's posts and replies to their comments. */
    Comments,

    /** Posts that mention the user. */
    MentionStatuses,

    /** Comments that mention the user. */
    MentionComments,

    /** Likes of the user's posts and comments. */
    Likes,
}

/** Which [NoticeKind]s have something the user hasn't seen. */
data class UnreadNotices(val kinds: Set<NoticeKind> = emptySet()) {
    val any: Boolean
        get() = kinds.isNotEmpty()

    fun has(kind: NoticeKind) = kind in kinds

    fun without(kind: NoticeKind) = UnreadNotices(kinds - kind)

    companion object {
        /**
         * Reads the counts of `remind/push_count`, which sit at the top level and may leave out the
         * ones that are 0 (PLAN §8).
         */
        fun fromJson(json: JsonObject): UnreadNotices {
            fun counts(vararg keys: String) = keys.any { (readInt(json[it]) ?: 0) > 0 }
            return UnreadNotices(
                buildSet {
                    if (counts("cmt")) add(NoticeKind.Comments)
                    if (counts("mention_status", "all_mention_status"))
                        add(NoticeKind.MentionStatuses)
                    if (counts("mention_cmt", "all_mention_cmt")) add(NoticeKind.MentionComments)
                    if (counts("attitude")) add(NoticeKind.Likes)
                }
            )
        }
    }
}

/**
 * A comment in the notifications, with the post it is on and, for a reply, the comment it answers.
 */
data class CommentNotice(
    val comment: Comment,
    val status: Status? = null,
    val replyTo: Comment? = null,
) {
    val id: String
        get() = comment.id

    companion object {
        fun tryFromJson(json: JsonElement?): CommentNotice? {
            val map = readMap(json) ?: return null
            val comment = Comment.tryFromJson(map) ?: return null
            return CommentNotice(
                comment = comment,
                status = Status.tryFromJson(map["status"]),
                replyTo = Comment.tryFromJson(map["reply_comment"]),
            )
        }
    }
}

/** Someone liking one of the user's posts or comments. */
data class LikeNotice(
    val id: String,
    /** Who liked it. */
    val user: WeiboUser? = null,
    val createdAt: Instant? = null,
    /** The post liked, or the one [comment] is on. */
    val status: Status? = null,
    /** The comment liked, if it was a comment. */
    val comment: Comment? = null,
    /**
     * False when [status] is the cut-down form Weibo sometimes sends, without pictures or counts
     * (PLAN §8), which the detail page shouldn't show while it loads the whole post.
     */
    val statusComplete: Boolean = false,
) {
    companion object {
        fun tryFromJson(json: JsonElement?): LikeNotice? {
            val map = readMap(json) ?: return null
            // `idStr`, unlike everywhere else.
            val id =
                readString(map["idStr"])
                    ?: readString(map["idstr"])
                    ?: readString(map["id"])
                    ?: return null
            val comment = readMap(map["comment"])
            val status = readMap(map["status"]) ?: readMap(comment?.get("status"))
            return LikeNotice(
                id = id,
                user = WeiboUser.tryFromJson(map["user"]),
                createdAt = parseWeiboDate(readString(map["created_at"])),
                status = Status.tryFromJson(status),
                comment = Comment.tryFromJson(comment),
                statusComplete = status?.containsKey("attitudes_count") == true,
            )
        }
    }
}

package io.github.leonfox28.maxbo.support

import kotlinx.serialization.json.JsonObject

// Hand-written, synthetic Weibo JSON shaped like the web API's responses.
// Never paste real responses here (see captured/ and PLAN §2).

fun userJson(id: Long = 1001, name: String = "示例用户"): JsonObject =
    obj(
        "id" to id,
        "idstr" to "$id",
        "screen_name" to name,
        "avatar_large" to
            "https://tvax1.sinaimg.cn/crop.0.0.180.180.180/abc.jpg?KID=imgbed,tva&Expires=1&ssig=x",
        "verified" to false,
        "verified_type" to -1,
    )

/** A `pic_infos` entry; a live photo's carries the URL of its [video], signed until [expires]. */
fun picJson(
    pid: String,
    type: String = "pic",
    width: Int = 1200,
    height: Int = 900,
    video: Boolean = type == "livephoto",
    expires: Long = 2000000000,
): JsonObject {
    fun variant(size: String, w: Int) =
        obj(
            "url" to "https://wx1.sinaimg.cn/$size/$pid.jpg",
            "width" to w,
            "height" to w * height / width,
        )
    return obj(
        "pic_id" to pid,
        "type" to type,
        "thumbnail" to variant("wap180", 180),
        "bmiddle" to variant("wap360", 360),
        "large" to variant("orj960", 960),
        "mw2000" to variant("mw2000", if (width < 2000) width else 2000),
        "original" to variant("orj1080", 1080),
        "largest" to variant("large", width),
        if (video) {
            "video" to
                "https://video.weibo.com/media/play?livephoto=https%3A%2F%2Fus.sinaimg.cn%2F" +
                    "$pid.mov&Expires=$expires&ssig=x&KID=unistore,video"
        } else {
            null
        },
    )
}

fun statusJson(
    id: Long = 5000000000000001,
    text: String = "一条示例微博",
    user: JsonObject? = null,
    pics: List<JsonObject> = emptyList(),
    retweeted: JsonObject? = null,
    pageInfo: JsonObject? = null,
    extra: Map<String, Any?> = emptyMap(),
): JsonObject =
    obj(
        "id" to id,
        "idstr" to "$id",
        "mid" to "$id",
        "mblogid" to "Abc$id",
        "created_at" to "Mon Oct 12 13:17:46 +0800 2026",
        "source" to "示例客户端",
        "region_name" to "发布于 上海",
        "text" to text,
        "text_raw" to text,
        "user" to (user ?: userJson()),
        "isLongText" to false,
        "pic_num" to pics.size,
        "pic_ids" to pics.map { it["pic_id"] },
        "pic_infos" to
            pics.associateBy { (it["pic_id"] as kotlinx.serialization.json.JsonPrimitive).content },
        retweeted?.let { "retweeted_status" to it },
        pageInfo?.let { "page_info" to it },
        "reposts_count" to 3,
        "comments_count" to 12345,
        "attitudes_count" to 0,
        "attitudes_status" to 0,
        "isAd" to false,
        "readtimetype" to "mblog",
    ) + extra

/** A friendstimeline response holding [statuses]. */
fun feedJson(statuses: List<JsonObject>, maxId: Long = 0): JsonObject =
    obj(
        "ok" to 1,
        "statuses" to statuses,
        "total_number" to statuses.size,
        "since_id" to (statuses.firstOrNull()?.get("id") ?: 0),
        "max_id" to maxId,
        "since_id_str" to (statuses.firstOrNull()?.get("idstr") ?: "0"),
        "max_id_str" to "$maxId",
    )

/**
 * A video page_info whose streams are signed until [expires] (Unix seconds); its streams and cover
 * are named after [name].
 */
fun videoPageInfo(expires: Long = 2000000000, name: String = "o0"): JsonObject {
    fun stream(label: String) =
        "http://f.video.weibocdn.com/$name/$label.mp4?label=$label&Expires=$expires&ssig=s&KID=unistore,video"
    return obj(
        "object_type" to "video",
        "page_pic" to
            if (name == "o0") "https://wx1.sinaimg.cn/orj480/cover.jpg"
            else "https://wx1.sinaimg.cn/orj480/$name.jpg",
        "media_info" to
            obj(
                "duration" to 49,
                "mp4_sd_url" to stream("mp4_ld"),
                "mp4_hd_url" to stream("mp4_hd"),
                "mp4_720p_mp4" to stream("mp4_720p"),
                "h265_mp4_hd" to stream("h265_hd"),
                "playback_list" to
                    arr(
                        obj(
                            "meta" to obj("label" to "scrubber_hd", "type" to 3),
                            "play_info" to
                                obj(
                                    "mime" to "image/jpeg",
                                    "url" to "https://wx3.sinaimg.cn/large/scrub.jpg",
                                ),
                        ),
                        obj(
                            "meta" to obj("label" to "mp4_hd", "quality_index" to 480, "type" to 1),
                            "play_info" to
                                obj(
                                    "mime" to "video/mp4",
                                    "protocol" to "general",
                                    "url" to stream("list_480"),
                                ),
                        ),
                        obj(
                            "meta" to
                                obj("label" to "mp4_1080p", "quality_index" to 1080, "type" to 1),
                            "play_info" to
                                obj(
                                    "mime" to "video/mp4",
                                    "protocol" to "general",
                                    "url" to stream("list_1080"),
                                ),
                        ),
                    ),
            ),
    )
}

/**
 * The fields of a post mixing pictures ([picJson]) and videos ([videoPageInfo]), in the order of
 * [items]; such posts have no `pic_infos` or `page_info`.
 */
fun mixedMediaFields(items: List<JsonObject>): Map<String, Any?> =
    mapOf(
        "pic_num" to items.size,
        "pic_ids" to items.mapNotNull { it["pic_id"] },
        "pic_infos" to null,
        "mix_media_info" to
            obj(
                "items" to
                    items.mapIndexed { i, item ->
                        if (
                            (item["object_type"] as? kotlinx.serialization.json.JsonPrimitive)
                                ?.content == "video"
                        ) {
                            obj("type" to "video", "id" to "v$i", "data" to item)
                        } else {
                            obj("type" to "pic", "id" to item["pic_id"], "data" to item)
                        }
                    }
            ),
    )

fun commentJson(
    id: Long = 6000000000000001,
    text: String = "一条示例评论",
    user: JsonObject? = null,
    likes: Int = 0,
    replyCount: Int = 0,
    replies: List<JsonObject> = emptyList(),
    urlStruct: List<JsonObject> = emptyList(),
): JsonObject {
    val ownPictures = urlStruct.filter { it["pic_infos"] != null }
    return obj(
        "id" to id,
        "idstr" to "$id",
        "rootid" to id,
        "created_at" to "Mon Oct 12 13:20:00 +0800 2026",
        "source" to "来自上海",
        "text" to text,
        // Like Weibo's, text_raw links the comment's own pictures.
        "text_raw" to
            (listOf(text) +
                    ownPictures.map {
                        (it["short_url"] as kotlinx.serialization.json.JsonPrimitive).content
                    })
                .joinToString(" "),
        "pic_num" to ownPictures.size,
        "user" to (user ?: userJson(id = 2001, name = "评论者")),
        "like_counts" to likes,
        "liked" to false,
        "total_number" to replyCount,
        "comments" to replies,
        "url_struct" to urlStruct,
    )
}

/**
 * A url_struct entry for a picture attached to a comment, which (unlike a status picture) has no
 * pic_id and no sizes.
 */
fun commentPicJson(pid: String): JsonObject =
    obj(
        "short_url" to "http://t.cn/$pid",
        "url_title" to "查看图片",
        "url_type" to 39,
        "pic_ids" to listOf(pid),
        "pic_infos" to
            obj(
                pid to
                    obj(
                        "thumbnail" to obj("url" to "https://wx4.sinaimg.cn/thumbnail/$pid.jpg"),
                        "bmiddle" to obj("url" to "https://wx4.sinaimg.cn/bmiddle/$pid.jpg"),
                        "large" to obj("url" to "https://wx4.sinaimg.cn/large/$pid.jpg"),
                        "woriginal" to obj("url" to "https://wx4.sinaimg.cn/woriginal/$pid.jpg"),
                    )
            ),
    )

/** A buildComments response; [maxId] 0 marks the last page. */
fun commentsPageJson(comments: List<JsonObject>, maxId: Long = 0, total: Int? = null): JsonObject =
    obj(
        "ok" to 1,
        "data" to comments,
        "rootComment" to emptyList<Any>(),
        "total_number" to (total ?: comments.size),
        "max_id" to maxId,
        "trendsText" to "",
    )

/** A repostTimeline response. */
fun repostsPageJson(reposts: List<JsonObject>, maxPage: Int = 1, total: Int? = null): JsonObject =
    obj(
        "ok" to 1,
        "data" to reposts,
        "total_number" to (total ?: reposts.size),
        "max_page" to maxPage,
    )

/** The `data` of a profile/info response for [user] (see [userJson]). */
fun profileDataJson(user: JsonObject? = null, extra: Map<String, Any?> = emptyMap()): JsonObject =
    obj(
        "user" to
            ((user ?: userJson()) +
                mapOf(
                    "description" to "这是一段简介",
                    "location" to "上海 浦东新区",
                    "gender" to "f",
                    "followers_count" to 123456,
                    "followers_count_str" to "12.3万",
                    "friends_count" to 321,
                    "statuses_count" to 4567,
                    "following" to true,
                    "follow_me" to false,
                    "cover_image_phone" to
                        "https://ww1.sinaimg.cn/crop.0.0.640.640.640/cover1.jpg;https://ww1.sinaimg.cn/crop.0.0.640.640.640/cover2.jpg",
                ) +
                extra),
        "tabList" to arr(obj("name" to "home", "tabName" to "精选")),
        "blockText" to "",
    )

/** A mymblog response; an empty [sinceId] marks the last page. */
fun userStatusesJson(
    statuses: List<JsonObject>,
    sinceId: String = "5100000000000000kp2",
    total: Int = 100,
): JsonObject =
    obj(
        "ok" to 1,
        "data" to
            obj(
                "since_id" to sinceId,
                "list" to statuses,
                "status_visible" to 0,
                "bottom_tips_visible" to false,
                "bottom_tips_text" to "",
                "topicList" to emptyList<Any>(),
                "total" to total,
            ),
    )

/** A side/hotSearch entry. Ranked entries have [realpos]; entries Weibo inserts don't. */
fun hotItemJson(
    word: String,
    realpos: Int? = null,
    num: Long = 1234567,
    scheme: String? = null,
    icon: String? = null,
    smallIcon: String? = null,
    color: String = "#ff9406",
    isAd: Int? = null,
): JsonObject =
    obj(
        "word" to word,
        "note" to word,
        "word_scheme" to (scheme ?: "#$word#"),
        "num" to num,
        "rank" to (realpos ?: 1) - 1,
        realpos?.let { "realpos" to it },
        "label_name" to (icon ?: ""),
        icon?.let { "icon_desc" to it },
        icon?.let { "icon_desc_color" to color },
        "small_icon_desc" to (smallIcon ?: icon ?: ""),
        "small_icon_desc_color" to if ((smallIcon ?: icon) == null) "" else color,
        "flag" to 1,
        isAd?.let { "is_ad" to it },
    )

/** A side/hotSearch response. */
fun hotSearchJson(
    realtime: List<JsonObject> = emptyList(),
    hotgovs: List<JsonObject> = emptyList(),
    hotgov: JsonObject? = null,
): JsonObject =
    obj(
        "ok" to 1,
        "data" to obj("realtime" to realtime, "hotgovs" to hotgovs, hotgov?.let { "hotgov" to it }),
    )

/**
 * One of the 消息 lists (message/cmt, comments/mentions, statuses/mentions, message/attitudes): items
 * under `data.<key>`; [nextCursor] 0 marks the last page.
 */
fun noticePageJson(key: String, items: List<JsonObject>, nextCursor: Long = 0): JsonObject =
    obj(
        "ok" to 1,
        "data" to
            obj(
                key to items,
                "next_cursor" to nextCursor,
                "previous_cursor" to 0,
                "total_number" to items.size,
                "miss_ids" to emptyList<Any>(),
            ),
    )

/** A comment as the notification lists have it, with the post it is on. */
fun commentNoticeJson(
    id: Long = 6000000000000001,
    text: String = "一条评论通知",
    status: JsonObject? = null,
    replyTo: JsonObject? = null,
): JsonObject =
    commentJson(id = id, text = text) +
        obj(
            "status" to (status ?: statusJson(text = "被评论的微博")),
            replyTo?.let { "reply_comment" to it },
        )

/**
 * A like as message/attitudes has it. With [cut], the post is the cut-down form Weibo sometimes
 * sends, without pictures or counts.
 */
fun likeNoticeJson(
    id: Long = 7000000000000001,
    status: JsonObject? = null,
    comment: JsonObject? = null,
    cut: Boolean = false,
): JsonObject {
    val post = status ?: statusJson(text = "被赞的微博")
    val shown =
        when {
            cut ->
                obj(
                    *listOf("id", "idstr", "mid", "mblogid", "text", "user")
                        .map { it to post[it] }
                        .toTypedArray()
                )
            comment == null -> post
            else -> null
        }
    return obj(
        "id" to id,
        "idStr" to "$id",
        "created_at" to "Mon Oct 12 14:00:00 +0800 2026",
        "attitude" to "heart",
        "user" to userJson(id = 3001, name = "点赞的人"),
        shown?.let { "status" to it },
        comment?.let { "comment" to it },
    )
}

/**
 * A statuses/search response. A search for a word comes with a [topicHeads] too, without a name:
 * see [topicHeadsJson].
 */
fun searchJson(
    statuses: List<JsonObject>,
    topicHeads: JsonObject? = null,
    total: Int = 700,
): JsonObject =
    obj(
        "ok" to 1,
        "statuses" to statuses,
        "total_number" to total,
        "absstr" to "",
        "intention" to 0,
        "topicHeads" to (topicHeads ?: topicHeadsJson()),
    )

/** The `topicHeads` of a search: for the topic [name], or for a word when null. */
fun topicHeadsJson(
    name: String? = null,
    summary: String = "",
    read: Long = 42262434,
    mention: Long = 9365,
    authors: Long = 1997,
): JsonObject =
    obj(
        "count" to
            obj(
                "read" to read,
                "mention" to mention,
                "ori_uv" to authors,
                "r_valid" to read - 1000,
                "interact" to 28912,
            ),
        "24h_read" to read / 2,
        "24h_mention" to mention / 2,
        "topic_ori" to (name ?: "某个词"),
        "object" to
            (obj(
                "media_counts" to 209,
                "summary" to summary,
                "image" to obj("url" to "https://wx1.sinaimg.cn/thumb180/topic.jpg"),
            ) +
                if (name != null) {
                    mapOf(
                        "display_name" to name,
                        "id" to "1022:231522topic",
                        "object_type" to "search_topic",
                        "url" to "https://s.weibo.com/weibo?q=%23$name%23",
                    )
                } else emptyMap()),
        "claim_info" to emptyList<Any>(),
        "band_info" to emptyList<Any>(),
    )

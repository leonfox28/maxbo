package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.core.util.parseWeiboDate
import io.github.leonfox28.maxbo.core.util.readBool
import io.github.leonfox28.maxbo.core.util.readInt
import io.github.leonfox28.maxbo.core.util.readMap
import io.github.leonfox28.maxbo.core.util.readMaps
import io.github.leonfox28.maxbo.core.util.readString
import io.github.leonfox28.maxbo.core.util.readUrl
import java.net.URLEncoder
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup

/** Who can see a post, as `visible.type` and `statuses/update` have it. */
enum class StatusVisibility(val code: Int, val label: String) {
    Public(0, "公开"),
    Friends(6, "好友圈"),
    OnlyMe(1, "仅自己可见"),
    Fans(10, "粉丝");

    companion object {
        /** The ones a new post can choose from. */
        val choices = listOf(Public, Friends, OnlyMe)

        /** Unknown kinds (paid members, groups) count as public. */
        fun of(code: Long?): StatusVisibility =
            entries.firstOrNull { it.code.toLong() == code } ?: Public
    }
}

/** A Weibo post ("微博"). */
data class Status(
    val id: String,
    /** The short id used in web URLs (`weibo.com/<uid>/<mblogid>`). */
    val mblogId: String? = null,
    /**
     * Null for deleted or hidden posts, which still appear as retweet sources. Retweet sources in
     * `statuses/mentions` have only an id and a name.
     */
    val user: WeiboUser? = null,
    val createdAt: Instant? = null,
    /** The posting client, e.g. `iPhone客户端`; plain text. */
    val source: String? = null,
    /** Where it was posted from, e.g. `发布于 北京`. */
    val region: String? = null,
    /** The body as HTML; see `parseStatusText`. */
    val text: String = "",
    /**
     * The body as plain text, in full, as a repost of this one quotes it; null when Weibo sent only
     * a cut-short one (long posts in lists).
     */
    val plainText: String? = null,
    /** Short links in [text] and what they resolve to. */
    val urls: List<UrlEntity> = emptyList(),
    val pics: List<PicInfo> = emptyList(),
    /** How many pictures the post has, which can exceed [pics]. */
    val picCount: Int = 0,
    /** The pictures and videos of a post mixing them, in order; such posts have neither. */
    val mixedMedia: List<MixedMedia> = emptyList(),
    val video: VideoInfo? = null,
    /** A link preview (topic, live stream, …) shown when there is no media. */
    val pageCard: PageCard? = null,
    val retweeted: Status? = null,
    val repostsCount: Int = 0,
    val commentsCount: Int = 0,
    val likesCount: Int = 0,
    val liked: Boolean = false,
    /** Marked as an ad by Weibo. Ads are labelled, never hidden (PLAN §2). */
    val isAd: Boolean = false,
    /** Pinned atop its author's profile; only set in profile lists. */
    val pinned: Boolean = false,
    /**
     * Why someone else's post is on a user's profile: Weibo puts posts they liked or commented on
     * among their own, labelled like `他10-17赞过的微博` (PLAN §8). Only set in profile lists.
     */
    val activity: String? = null,
    /** Who can see it. Others' posts are public as far as the viewer knows. */
    val visibility: StatusVisibility = StatusVisibility.Public,
) {
    /** The videos of a post mixing pictures and videos, in order. */
    val mixedVideos: List<VideoInfo>
        get() = mixedMedia.filterIsInstance<MixedMedia.Video>().map { it.video }

    /** Its video, or with an [index] the one at [index] in [mixedVideos]. */
    fun videoAt(index: Int?): VideoInfo? =
        if (index == null) video else mixedVideos.getOrNull(index)

    companion object {
        /**
         * [parentUrls] is the `url_struct` of the post retweeting this one, which also covers the
         * links in this one's text, and [parentPage] its `page_info`.
         */
        fun tryFromJson(
            json: JsonElement?,
            parentUrls: List<JsonObject> = emptyList(),
            parentPage: JsonObject? = null,
        ): Status? {
            val map = readMap(json) ?: return null
            val id =
                readString(map["idstr"])
                    ?: readString(map["mid"])
                    ?: readString(map["id"])
                    ?: return null

            val pics = picsOf(map)
            val pageInfo = readMap(map["page_info"])
            val ownUrls = readMaps(map["url_struct"])
            val urlStruct = ownUrls.ifEmpty { parentUrls }

            return Status(
                id = id,
                mblogId = readString(map["mblogid"]),
                user = WeiboUser.tryFromJson(map["user"]) ?: authorOf(map, parentPage),
                createdAt = parseWeiboDate(readString(map["created_at"])),
                source = plainText(readString(map["source"])),
                region = readString(map["region_name"]),
                text = textOf(map, urlStruct),
                plainText =
                    readString(readMap(map["longText"])?.get("content"))
                        ?: if (readBool(map["isLongText"]) == true) null
                        else readString(map["text_raw"]),
                urls = urlStruct.mapNotNull(UrlEntity::tryFromJson),
                pics = pics,
                picCount = readInt(map["pic_num"])?.toInt() ?: pics.size,
                mixedMedia =
                    readMaps(readMap(map["mix_media_info"])?.get("items"))
                        .mapNotNull(MixedMedia::tryFromJson),
                video = VideoInfo.tryFromPageInfo(pageInfo),
                pageCard = PageCard.tryFromPageInfo(pageInfo, urlStruct),
                retweeted =
                    tryFromJson(
                        map["retweeted_status"],
                        parentUrls = ownUrls,
                        parentPage = pageInfo,
                    ),
                repostsCount = readInt(map["reposts_count"])?.toInt() ?: 0,
                commentsCount = readInt(map["comments_count"])?.toInt() ?: 0,
                likesCount = readInt(map["attitudes_count"])?.toInt() ?: 0,
                liked = readInt(map["attitudes_status"]) == 1L,
                isAd = isAd(map),
                pinned = readInt(map["isTop"]) == 1L,
                activity = activityOf(map),
                visibility = StatusVisibility.of(readInt(readMap(map["visible"])?.get("type"))),
            )
        }

        // Not yet confirmed against a real feed ad; see PLAN §8 item 5. Weibo
        // also sets all of these on ordinary posts it classes as advertising
        // content (mark ending `WeiboADNatural`, seen on users' own reposts);
        // those are not ads placed in the feed, so they get no label.
        private fun isAd(map: JsonObject): Boolean {
            val mark = readString(map["mark"]).orEmpty()
            if (mark.contains("WeiboADNatural")) return false
            return readBool(map["isAd"]) == true ||
                readString(map["readtimetype"]) == "adMblog" ||
                mark.contains("mark_ad")
        }

        // Such a label links (`title.structs`) to that day's whole list in
        // Weibo's app, and ends in its length, e.g. `(10)`; we can't open the
        // list, so the length goes. Other titles, such as 公开 in the home
        // feed, link nowhere.
        private fun activityOf(map: JsonObject): String? {
            val title = readMap(map["title"]) ?: return null
            if (readMaps(title["structs"]).isEmpty()) return null
            return readString(title["text"])?.replaceFirst(Regex("""\(\d+\)$"""), "")?.ifEmpty {
                null
            }
        }

        // Retweet sources in `statuses/mentions` come without `user`. Their
        // retweeting post's page_info is a card for them instead, titled
        // `@author`, which weibo.com shows in their place (PLAN §8).
        private fun authorOf(map: JsonObject, parentPage: JsonObject?): WeiboUser? {
            val mblogId = readString(map["mblogid"])
            if (
                parentPage == null ||
                    mblogId == null ||
                    readString(parentPage["mblogid"]) != mblogId
            ) {
                return null
            }
            val id = readString(parentPage["uidPageInfo"]) ?: return null
            val title = readString(parentPage["content1"]) ?: return null
            if (!title.startsWith("@")) return null
            val name = title.substring(1).trim()
            return if (name.isEmpty()) null else WeiboUser(id = id, screenName = name)
        }

        // Retweet sources in `statuses/mentions` come without `text` or
        // `user`, only the plain `text_raw`, cut short with no marker if the
        // post is long (PLAN §8).
        private fun textOf(map: JsonObject, urlStruct: List<JsonObject>): String {
            readString(map["text"])?.let {
                return it
            }
            val raw = readString(map["text_raw"]) ?: return ""
            val html = rawTextAsHtml(raw, urlStruct)
            return if (readBool(map["isLongText"]) == true) {
                "$html ...<span class=\"expand\">展开</span>"
            } else {
                html
            }
        }

        // Links, @mentions and #topics# in plain text. Links stop at the
        // first non-ASCII character: `text_raw` runs them straight into the
        // text.
        private val rawEntity =
            Regex(
                """https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""" +
                    """|(?<![A-Za-z0-9])@[A-Za-z0-9_\-\x{4e00}-\x{9fa5}]{1,30}""" +
                    """|#[^#\n]{1,64}#"""
            )

        /**
         * [raw] as the HTML that `text` would have held, with links titled as `url_struct` says.
         */
        private fun rawTextAsHtml(raw: String, urlStruct: List<JsonObject>): String {
            val html = StringBuilder()
            var last = 0
            for (match in rawEntity.findAll(raw)) {
                html.append(escapeHtml(raw.substring(last, match.range.first)))
                last = match.range.last + 1
                val entity = match.value
                val label = escapeHtml(entity)
                when {
                    entity.startsWith("@") ->
                        html.append("<a href=\"/n/${label.substring(1)}\">$label</a>")
                    entity.startsWith("#") -> {
                        val query = URLEncoder.encode(entity, "UTF-8")
                        html.append("<a href=\"https://s.weibo.com/weibo?q=$query\">$label</a>")
                    }
                    else -> {
                        val info = urlStruct.firstOrNull { readString(it["short_url"]) == entity }
                        // `url_type_pic` names icons that aren't there, so
                        // links go without.
                        val title = readString(info?.get("url_title"))
                        html.append("<a href=\"$label\">${title?.let(::escapeHtml) ?: label}</a>")
                    }
                }
            }
            html.append(escapeHtml(raw.substring(last)))
            return html.toString()
        }

        private fun escapeHtml(text: String) =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")

        // `source` is plain text or an HTML fragment like
        // `<a href=…>荣耀Magic</a>`.
        private fun plainText(fragment: String?): String? {
            if (fragment == null) return null
            val text =
                if (fragment.contains('<'))
                    Jsoup.parseBodyFragment(fragment).body().wholeText().trim()
                else fragment.trim()
            return text.ifEmpty { null }
        }
    }
}

/** A short link (`t.cn/…`) in a status body. */
data class UrlEntity(val shortUrl: String, val longUrl: String? = null, val title: String? = null) {
    companion object {
        fun tryFromJson(json: JsonObject): UrlEntity? {
            val short = readUrl(json["short_url"]) ?: return null
            return UrlEntity(
                shortUrl = short,
                longUrl = readUrl(json["long_url"]) ?: readUrl(json["ori_url"]),
                title = readString(json["url_title"]),
            )
        }
    }
}

/** The video attached to a status. */
data class VideoInfo(
    val coverUrl: String? = null,
    /** Length in seconds. */
    val duration: Int? = null,
    val aspectRatio: Float? = null,
    /**
     * Playable mp4 streams, preferred first. They are signed and expire about an hour after Weibo
     * sent them; see `resolveVideoUrl`.
     */
    val streamUrls: List<String> = emptyList(),
) {
    companion object {
        // In order of preference: 720p is plenty on a phone; H.265 renditions
        // are skipped because not every device decodes them.
        private val streamKeys = listOf("mp4_720p_mp4", "mp4_hd_url", "mp4_sd_url")

        fun tryFromPageInfo(pageInfo: JsonObject?): VideoInfo? {
            if (pageInfo == null || readString(pageInfo["object_type"]) != "video") return null
            val media = readMap(pageInfo["media_info"])
            val big =
                readMap(readMap(media?.get("big_pic_info"))?.get("pic_big"))
                    ?: readMap(readMap(pageInfo["pic_info"])?.get("pic_big"))
            val width = readInt(big?.get("width"))
            val height = readInt(big?.get("height"))
            val streams = LinkedHashSet<String>()
            streamKeys.mapNotNullTo(streams) { readUrl(media?.get(it)) }
            streams += playbackUrls(media?.get("playback_list"))
            return VideoInfo(
                coverUrl = readUrl(pageInfo["page_pic"]) ?: readUrl(big?.get("url")),
                duration = readInt(media?.get("duration"))?.toInt(),
                aspectRatio =
                    if (width != null && height != null && width > 0 && height > 0) {
                        width.toFloat() / height
                    } else null,
                streamUrls = streams.map(::https),
            )
        }

        // Stream URLs come as plain http, which Android refuses by default;
        // the CDN serves the same signed URLs over https, as weibo.com uses
        // them.
        private fun https(url: String) =
            if (url.startsWith("http://")) "https://${url.substring(7)}" else url

        /**
         * The plain mp4 entries of `playback_list`, best quality first; it also lists scrubbing
         * thumbnails and may list adaptive streams.
         */
        private fun playbackUrls(list: JsonElement?): List<String> =
            readMaps(list)
                .mapNotNull { item ->
                    val info = readMap(item["play_info"]) ?: return@mapNotNull null
                    if (
                        readString(info["mime"]) != "video/mp4" ||
                            readString(info["protocol"]) == "dash"
                    ) {
                        return@mapNotNull null
                    }
                    val url = readUrl(info["url"]) ?: return@mapNotNull null
                    url to (readInt(readMap(item["meta"])?.get("quality_index")) ?: 0L)
                }
                .sortedByDescending { it.second }
                .map { it.first }
    }
}

/** An item of a post mixing pictures and videos (`mix_media_info`). */
sealed interface MixedMedia {
    data class Pic(val pic: PicInfo) : MixedMedia

    data class Video(val video: VideoInfo) : MixedMedia

    companion object {
        /** Items hold a `pic_infos` entry or a `page_info` as their `data`. */
        fun tryFromJson(json: JsonObject): MixedMedia? {
            val data = readMap(json["data"])
            return when (readString(json["type"])) {
                "pic" -> PicInfo.tryFromJson(data, id = readString(json["id"]))?.let(::Pic)
                "video" -> VideoInfo.tryFromPageInfo(data)?.let(::Video)
                else -> null
            }
        }
    }
}

/** A link preview such as a topic or live stream. */
data class PageCard(
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
) {
    companion object {
        /**
         * Null when the card would only repeat the title of a link in the text, [urlStruct]
         * describing those links: a `webpage` card can come with no picture or description (PLAN
         * §8).
         */
        fun tryFromPageInfo(
            pageInfo: JsonObject?,
            urlStruct: List<JsonObject> = emptyList(),
        ): PageCard? {
            if (pageInfo == null || readString(pageInfo["object_type"]) == "video") return null
            val title = readString(pageInfo["page_title"]) ?: return null
            val description = readString(pageInfo["page_desc"]) ?: readString(pageInfo["content2"])
            val imageUrl = readUrl(pageInfo["page_pic"])
            if (
                description == null &&
                    imageUrl == null &&
                    urlStruct.any { readString(it["url_title"]) == title }
            ) {
                return null
            }
            return PageCard(title = title, description = description, imageUrl = imageUrl)
        }
    }
}

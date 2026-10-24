package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.support.feedJson
import io.github.leonfox28.maxbo.support.mixedMediaFields
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.picJson
import io.github.leonfox28.maxbo.support.plus
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.userJson
import io.github.leonfox28.maxbo.support.videoPageInfo
import io.github.leonfox28.maxbo.support.without
import io.github.leonfox28.maxbo.ui.widget.text.StatusLink
import io.github.leonfox28.maxbo.ui.widget.text.TextSegment
import io.github.leonfox28.maxbo.ui.widget.text.parseStatusText
import io.github.leonfox28.maxbo.ui.widget.text.plainStatusText
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTest {
    private fun parse(json: JsonObject) = Status.tryFromJson(json)!!

    @Test
    fun `parses a plain status, keeping ids as strings`() {
        val s = parse(statusJson(id = 5000000000000042))

        assertEquals("5000000000000042", s.id)
        assertEquals("Abc5000000000000042", s.mblogId)
        assertEquals("1001", s.user?.id)
        assertEquals("示例用户", s.user?.screenName)
        assertNull(s.user?.verifiedType)
        assertEquals(Instant.parse("2026-10-12T05:17:46Z"), s.createdAt)
        assertEquals("示例客户端", s.source)
        assertEquals("发布于 上海", s.region)
        assertEquals(12345, s.commentsCount)
        assertFalse(s.liked)
        assertFalse(s.isAd)
        assertTrue(s.pics.isEmpty())
        assertNull(s.retweeted)
    }

    @Test
    fun `tolerates missing and mistyped fields`() {
        assertNull(Status.tryFromJson(null))
        assertNull(Status.tryFromJson(obj("text" to "no id")))

        val s =
            parse(
                obj(
                    "idstr" to "7",
                    "user" to "not a map",
                    "pic_infos" to emptyList<Any>(),
                    "reposts_count" to "12",
                    "attitudes_count" to null,
                    "created_at" to "yesterday",
                    "source" to "",
                )
            )
        assertNull(s.user)
        assertTrue(s.pics.isEmpty())
        assertEquals(12, s.repostsCount)
        assertEquals(0, s.likesCount)
        assertNull(s.createdAt)
        assertNull(s.source)
        assertEquals("", s.text)
    }

    @Test
    fun `strips HTML from source`() {
        val s =
            parse(
                statusJson(
                    extra =
                        mapOf(
                            "source" to
                                "<a target=\"_blank\" href=\"https://x.example/\">某某&amp;客户端</a>"
                        )
                )
            )
        assertEquals("某某&客户端", s.source)
    }

    @Test
    fun `orders pictures by pic_ids and reads their types`() {
        val s =
            parse(
                statusJson(
                    pics = listOf(picJson("b"), picJson("a", type = "livephoto")),
                    extra = mapOf("pic_ids" to listOf("a", "b", "missing"), "pic_num" to 12),
                )
            )
        assertEquals(listOf("a", "b"), s.pics.map { it.id })
        assertEquals(PicType.LivePhoto, s.pics.first().type)
        assertEquals(12, s.picCount)
    }

    @Test
    fun `labels ads by any known marker, and never drops them`() {
        fun isAd(extra: Map<String, Any?>) = parse(statusJson(extra = extra)).isAd
        assertTrue(isAd(mapOf("isAd" to true)))
        assertTrue(isAd(mapOf("readtimetype" to "adMblog")))
        assertTrue(isAd(mapOf("mark" to "1_reallog_mark_ad:123|abc")))
        assertFalse(isAd(mapOf("mark" to "something_else")))
        // Content Weibo classed as advertising, not an ad placed in the feed.
        assertFalse(
            isAd(
                mapOf(
                    "isAd" to true,
                    "readtimetype" to "adMblog",
                    "mark" to "999_reallog_mark_ad:999|WeiboADNatural",
                )
            )
        )

        val page =
            FeedPage.fromJson(
                feedJson(
                    listOf(statusJson(id = 1), statusJson(id = 2, extra = mapOf("isAd" to true)))
                )
            )
        assertEquals(listOf(false, true), page.statuses.map { it.isAd })
    }

    @Test
    fun `reads verified users and liked state`() {
        val s =
            parse(
                statusJson(
                    user = userJson() + mapOf("verified" to true, "verified_type" to 0),
                    extra = mapOf("attitudes_status" to 1),
                )
            )
        assertEquals(0, s.user?.verifiedType)
        assertTrue(s.liked)
    }

    @Test
    fun `parses a retweet whose source was deleted`() {
        val s =
            parse(
                statusJson(
                    text = "转发",
                    retweeted = obj("idstr" to "9", "text" to "抱歉，此微博已被作者删除。", "user" to null),
                )
            )
        assertEquals("9", s.retweeted?.id)
        assertNull(s.retweeted?.user)
        assertEquals("抱歉，此微博已被作者删除。", s.retweeted?.text)
    }

    @Test
    fun `keeps the plain text only while it is whole`() {
        fun withExtra(extra: Map<String, Any?>) =
            parse(statusJson(text = "<b>正文</b>", extra = extra))

        assertEquals("正文", withExtra(mapOf("text_raw" to "正文")).plainText)
        // Lists cut long posts short; their detail has all of it.
        val cut = mapOf("isLongText" to true, "text_raw" to "正")
        assertNull(withExtra(cut).plainText)
        assertEquals(
            "正文全文",
            withExtra(cut + mapOf("longText" to obj("content" to "正文全文"))).plainText,
        )
    }

    @Test
    fun `falls back to text_raw for a retweet source in mentions`() {
        fun retweetOf(source: JsonObject) =
            parse(statusJson(text = "转发", retweeted = source)).retweeted!!
        val stub =
            statusJson(id = 9, text = "", extra = mapOf("text_raw" to "a<b>\n第二行")).without("user")

        val short = retweetOf(stub)
        assertNull(short.user)
        assertEquals("a&lt;b&gt;\n第二行", short.text)
        assertEquals("a<b> 第二行", plainStatusText(short.text))

        val long = retweetOf(stub + mapOf("isLongText" to true))
        assertEquals(TextSegment.Expand, parseStatusText(long.text).last())
    }

    @Test
    fun `takes a mentions retweet source's author from the retweeting post's page_info`() {
        val source =
            statusJson(id = 9, text = "", extra = mapOf("text_raw" to "原文")).without("user")
        fun retweetOf(card: JsonObject) =
            parse(statusJson(text = "转发", retweeted = source, pageInfo = card)).retweeted!!
        // As in `statuses/mentions`: a card for the source, not a link preview.
        val card =
            obj(
                "object_type" to "meaasgeObjType",
                "content1" to "@原作者",
                "content2" to "原文摘要",
                "page_pic" to "https://wx1.sinaimg.cn/thumbnail/a.jpg",
                "uidPageInfo" to "42",
                "mblogid" to "Abc9",
            )

        val author = retweetOf(card).user!!
        assertEquals("42", author.id)
        assertEquals("原作者", author.screenName)
        assertNull(author.avatarUrl)
        assertNull(retweetOf(card + mapOf("mblogid" to "Abc8")).user)
        assertNull(retweetOf(card + mapOf("content1" to "原作者")).user)

        val outer = parse(statusJson(text = "转发", retweeted = source, pageInfo = card))
        assertNull(outer.pageCard)
        assertNull(outer.video)
    }

    @Test
    fun `links up text_raw as text would, titling links from the retweeting post`() {
        val s =
            parse(
                statusJson(
                    text = "转发",
                    retweeted =
                        statusJson(
                            id = 9,
                            text = "",
                            extra = mapOf("text_raw" to "看http://t.cn/A1b2正文，问a@b.cn和@某人 #话题#"),
                        ) + mapOf("url_struct" to null),
                    extra =
                        mapOf(
                            "url_struct" to
                                listOf(
                                    obj(
                                        "short_url" to "http://t.cn/A1b2",
                                        "long_url" to "https://weibo.com/1/Abc9",
                                        "url_title" to "微博正文",
                                    )
                                )
                        ),
                )
            )
        val source = s.retweeted!!

        val links =
            parseStatusText(source.text, urls = source.urls)
                .filterIsInstance<TextSegment.Link>()
                .map { it.link }
        assertEquals(
            listOf(
                StatusLink.Web("https://weibo.com/1/Abc9".toHttpUrl()),
                StatusLink.Mention("某人"),
                StatusLink.Topic("话题"),
            ),
            links,
        )
        assertEquals("看微博正文正文，问a@b.cn和@某人 #话题#", plainStatusText(source.text))
    }

    @Test
    fun `a link to the pictures of a repost along the chain opens them all`() {
        // Weibo links to the first picture and names them all in data-pids.
        val s =
            parse(
                statusJson(
                    text =
                        "转发//<a href=/n/某人 usercard=\"name=@某人\">@某人</a>:看图 " +
                            "<a target=\"_blank\" data-pids=p1,p2 " +
                            "href=\"https://wx4.sinaimg.cn/large/p1.jpg\">" +
                            "<img class=\"icon-link\" title=\"http://t.cn/Pics\" " +
                            "src=\"https://h5.sinaimg.cn/photo.png\"/>查看2张图片</a>",
                    extra =
                        mapOf(
                            "url_struct" to
                                listOf(
                                    obj(
                                        "short_url" to "http://t.cn/Pics",
                                        "url_title" to "查看2张图片",
                                        "url_type" to 39,
                                        "pic_ids" to listOf("p1", "p2"),
                                        "pic_infos" to
                                            obj(
                                                "p2" to picJson("p2", width = 1080, height = 1574),
                                                "p1" to picJson("p1", width = 1080, height = 1448),
                                            ),
                                    )
                                )
                        ),
                )
            )

        val link =
            parseStatusText(s.text, urls = s.urls)
                .filterIsInstance<TextSegment.Link>()
                .single { it.text == "查看2张图片" }
                .link as StatusLink.Image
        assertEquals(listOf("p1", "p2"), link.pics.map { it.id })
        assertEquals(1080f / 1574, link.pics[1].aspectRatio!!, 0.001f)
    }

    @Test
    fun `reads a video cover, length and shape`() {
        val s =
            parse(
                statusJson(
                    pageInfo =
                        obj(
                            "object_type" to "video",
                            "page_pic" to "https://wx1.sinaimg.cn/orj480/cover.jpg",
                            "media_info" to
                                obj(
                                    "duration" to 95,
                                    "big_pic_info" to
                                        obj(
                                            "pic_big" to
                                                obj(
                                                    "url" to "//wx1.sinaimg.cn/big.jpg",
                                                    "width" to 1280,
                                                    "height" to 720,
                                                )
                                        ),
                                ),
                        )
                )
            )
        assertEquals("https://wx1.sinaimg.cn/orj480/cover.jpg", s.video?.coverUrl)
        assertEquals(95, s.video?.duration)
        assertEquals(16f / 9, s.video?.aspectRatio!!, 0.001f)
        assertNull(s.pageCard)
    }

    @Test
    fun `lists playable streams over https, preferred first`() {
        val s = parse(statusJson(pageInfo = videoPageInfo()))
        val labels = s.video!!.streamUrls.map { it.toHttpUrl().pathSegments.last() }
        // 720p first, then the hd/sd keys, then the best of playback_list;
        // H.265 and the scrubbing thumbnails are left out.
        assertEquals(
            listOf("mp4_720p.mp4", "mp4_hd.mp4", "mp4_ld.mp4", "list_1080.mp4", "list_480.mp4"),
            labels,
        )
        assertTrue(s.video!!.streamUrls.all { it.startsWith("https://") })
    }

    @Test
    fun `reads the pictures and videos of a post mixing them`() {
        val s =
            parse(
                statusJson(
                    extra =
                        mixedMediaFields(
                            listOf(
                                videoPageInfo(name = "first"),
                                picJson("p1"),
                                videoPageInfo(name = "second"),
                            )
                        )
                )
            )

        assertTrue(s.pics.isEmpty())
        assertNull(s.video)
        assertEquals(3, s.picCount)
        assertTrue(s.mixedMedia[0] is MixedMedia.Video)
        assertEquals("p1", (s.mixedMedia[1] as MixedMedia.Pic).pic.id)
        assertTrue(s.mixedMedia[2] is MixedMedia.Video)
        assertEquals(
            listOf(
                "https://wx1.sinaimg.cn/orj480/first.jpg",
                "https://wx1.sinaimg.cn/orj480/second.jpg",
            ),
            s.mixedVideos.map { it.coverUrl },
        )
        assertTrue(s.videoAt(1)!!.streamUrls.first().contains("/second/"))
        assertNull(s.videoAt(2))
    }

    @Test
    fun `reads other page_info as a link card`() {
        val s =
            parse(
                statusJson(
                    pageInfo =
                        obj(
                            "object_type" to "topic",
                            "page_title" to "#示例话题#",
                            "page_desc" to "一段描述",
                            "page_pic" to "//wx2.sinaimg.cn/large/topic.jpg",
                        )
                )
            )
        assertNull(s.video)
        assertEquals("#示例话题#", s.pageCard?.title)
        assertEquals("一段描述", s.pageCard?.description)
        assertEquals("https://wx2.sinaimg.cn/large/topic.jpg", s.pageCard?.imageUrl)
    }

    @Test
    fun `drops a bare link card that repeats a link in the text`() {
        fun withDesc(desc: String?) =
            parse(
                statusJson(
                    extra =
                        mapOf(
                            "url_struct" to
                                listOf(
                                    obj(
                                        "short_url" to "http://t.cn/A6abcdEF",
                                        "url_title" to "示例活动",
                                        "page_id" to "100120abc",
                                    )
                                )
                        ),
                    pageInfo =
                        obj(
                            "object_type" to "webpage",
                            "page_id" to "100120abc",
                            "page_title" to "示例活动",
                            desc?.let { "page_desc" to it },
                        ),
                )
            )
        assertNull(withDesc(desc = "").pageCard)
        assertEquals("示例活动", withDesc(desc = "一段描述").pageCard?.title)
    }

    @Test
    fun `reads whether it is pinned to its profile`() {
        assertTrue(parse(statusJson(extra = mapOf("isTop" to 1, "mblogtype" to 2))).pinned)
        assertFalse(parse(statusJson()).pinned)
    }

    @Test
    fun `reads why a post by someone else is on a profile`() {
        fun withTitle(title: JsonObject) = parse(statusJson(extra = mapOf("title" to title)))
        fun linked(text: String) =
            obj(
                "text" to text,
                "structs" to
                    listOf(
                        obj(
                            "name" to text,
                            "scheme" to "sinaweibo://cardlist?containerid=1001_-_comment",
                        )
                    ),
            )
        // Without the length of the list it links to, which we can't open.
        assertEquals("他10-17赞过的微博", withTitle(linked("他10-17赞过的微博(10)")).activity)
        assertEquals("他1小时前评论过的微博", withTitle(linked("他1小时前评论过的微博")).activity)
        // Other titles link nowhere.
        assertNull(
            withTitle(
                    obj(
                        "text" to "公开",
                        "base_color" to 1,
                        "icon_url" to "https://h5.sinaimg.cn/upload/a.png",
                    )
                )
                .activity
        )
        assertNull(parse(statusJson()).activity)
    }

    @Test
    fun `pic info picks the smallest variant that covers the size`() {
        val pic = PicInfo.tryFromJson(picJson("p", width = 1200, height = 900))!!
        // Shorter sides: 135, 270, 720, 900 (largest/original are excluded).
        assertTrue(pic.variantFor(100f).url.contains("/wap180/"))
        assertTrue(pic.variantFor(300f).url.contains("/orj960/"))
        assertTrue(pic.variantFor(5000f).url.contains("/mw2000/"))
        assertTrue(pic.largest!!.url.contains("/large/"))
        assertEquals(4f / 3, pic.aspectRatio!!, 0.001f)
        assertFalse(pic.isLong)
    }

    @Test
    fun `pic info flags long pictures`() {
        assertTrue(PicInfo.tryFromJson(picJson("p", width = 600, height = 4000))!!.isLong)
    }

    @Test
    fun `pic info needs an id and at least one usable variant`() {
        assertNull(PicInfo.tryFromJson(obj("pic_id" to "p")))
        assertNull(
            PicInfo.tryFromJson(obj("thumbnail" to obj("url" to "https://wx1.sinaimg.cn/a.jpg")))
        )
    }

    @Test
    fun `views the largest list rendition, or the whole file for a GIF`() {
        val pic = PicInfo.tryFromJson(picJson("p", width = 3000, height = 2000))!!
        assertTrue(pic.viewerVariant.url.contains("/mw2000/"))

        val gif = PicInfo.tryFromJson(picJson("g", type = "gif"))!!
        assertTrue(gif.viewerVariant.url.contains("/large/"))
    }

    @Test
    fun `wraps a linked picture URL`() {
        val pic = PicInfo.fromUrl("https://wx1.sinaimg.cn/large/p1.gif")
        assertEquals(PicType.Gif, pic.type)
        assertEquals("https://wx1.sinaimg.cn/large/p1.gif", pic.viewerVariant.url)
    }

    @Test
    fun `picsOf follows pic_ids and names pictures by their key`() {
        val pics =
            picsOf(
                obj(
                    "pic_ids" to listOf("b", "a"),
                    "pic_infos" to
                        obj(
                            "a" to picJson("a"),
                            "b" to
                                obj(
                                    "thumbnail" to
                                        obj("url" to "https://wx1.sinaimg.cn/thumbnail/b.jpg")
                                ),
                        ),
                )
            )
        assertEquals(listOf("b", "a"), pics.map { it.id })
    }

    @Test
    fun `a feed page continues from max_id_str`() {
        val page =
            FeedPage.fromJson(
                feedJson(listOf(statusJson())) +
                    mapOf("max_id" to 1, "max_id_str" to "5000000000000000123")
            )
        assertEquals("5000000000000000123", page.nextCursor)
    }

    @Test
    fun `a feed page ends at max_id 0 or an empty page`() {
        assertNull(FeedPage.fromJson(feedJson(listOf(statusJson()))).nextCursor)
        assertNull(FeedPage.fromJson(feedJson(emptyList(), maxId = 99)).nextCursor)
    }

    @Test
    fun `a feed page skips statuses it cannot read`() {
        val page =
            FeedPage.fromJson(
                obj(
                    "statuses" to
                        listOf(statusJson(id = 1), JsonPrimitive("junk"), obj("text" to "no id"))
                )
            )
        assertEquals(listOf("1"), page.statuses.map { it.id })
    }
}

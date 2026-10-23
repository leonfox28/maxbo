package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentNoticeJson
import io.github.leonfox28.maxbo.support.hotItemJson
import io.github.leonfox28.maxbo.support.hotSearchJson
import io.github.leonfox28.maxbo.support.likeNoticeJson
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.plus
import io.github.leonfox28.maxbo.support.profileDataJson
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.topicHeadsJson
import io.github.leonfox28.maxbo.support.userJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `user_profile_test`, `hot_search_test`, `topic_test` and `notice_test`. */
class K4ModelsTest {
    // user_profile_test

    @Test
    fun `reads a profile`() {
        val profile =
            UserProfile.tryFromJson(profileDataJson(user = userJson(id = 1001, name = "示例用户")))!!

        assertEquals("1001", profile.user.id)
        assertEquals("示例用户", profile.user.screenName)
        assertEquals("这是一段简介", profile.description)
        assertEquals("上海 浦东新区", profile.location)
        assertEquals("f", profile.gender)
        assertEquals(123456L, profile.followersCount)
        assertEquals(321L, profile.friendsCount)
        assertEquals(4567L, profile.statusesCount)
        assertTrue(profile.following)
        assertFalse(profile.followsMe)
    }

    @Test
    fun `takes the first of several cover images`() {
        assertEquals(
            "https://ww1.sinaimg.cn/crop.0.0.640.640.640/cover1.jpg",
            UserProfile.tryFromJson(profileDataJson())!!.coverUrl,
        )
    }

    @Test
    fun `keeps the verification reason only for verified users`() {
        val verified =
            UserProfile.tryFromJson(
                profileDataJson(
                    user = userJson() + mapOf("verified" to true, "verified_type" to 0),
                    extra = mapOf("verified_reason" to "知名博主"),
                )
            )!!
        val unverified =
            UserProfile.tryFromJson(profileDataJson(extra = mapOf("verified_reason" to "知名博主")))!!

        assertEquals("知名博主", verified.verifiedReason)
        assertNull(unverified.verifiedReason)
    }

    @Test
    fun `blank fields are absent`() {
        val profile =
            UserProfile.tryFromJson(
                profileDataJson(
                    extra =
                        mapOf("description" to "  ", "location" to "", "cover_image_phone" to "")
                )
            )!!

        assertNull(profile.description)
        assertNull(profile.location)
        assertNull(profile.coverUrl)
    }

    @Test
    fun `no user, no profile`() {
        // What profile/info sends for an unknown name, minus its message.
        assertNull(UserProfile.tryFromJson(null))
        assertNull(UserProfile.tryFromJson(obj("user" to obj())))
    }

    // hot_search_test

    private fun parse(json: JsonObject) = HotSearch.fromJson(json.getValue("data").jsonObject)

    @Test
    fun `reads ranked entries`() {
        val hot =
            parse(
                hotSearchJson(
                    realtime =
                        listOf(
                            hotItemJson("第一条", realpos = 1, icon = "热", num = 1587784),
                            hotItemJson("第二条", realpos = 2, scheme = "第二条"),
                        )
                )
            )

        val (first, second) = hot.items
        assertEquals("第一条", first.word)
        assertEquals("#第一条#", first.query)
        assertEquals(1, first.rank)
        assertEquals(1587784L, first.heat)
        assertEquals("热", first.badge)
        assertEquals(0xFFFF9406L, first.badgeColor)
        assertFalse(first.isAd)
        // Not every entry is a topic.
        assertEquals("第二条", second.query)
        assertEquals(2, second.rank)
        assertNull(second.badge)
    }

    @Test
    fun `keeps inserted and paid entries in place, labelled`() {
        val hot =
            parse(
                hotSearchJson(
                    realtime =
                        listOf(
                            hotItemJson("第一条", realpos = 1),
                            hotItemJson(
                                "付费的一条",
                                icon = "首发",
                                smallIcon = "商",
                                color = "#00b7ee",
                                isAd = 1,
                            ),
                            hotItemJson("辟谣的一条", icon = "辟谣", smallIcon = "", isAd = 1),
                            hotItemJson("没有标签的一条", isAd = 1),
                            hotItemJson("第二条", realpos = 2),
                        )
                )
            )

        assertEquals(listOf("第一条", "付费的一条", "辟谣的一条", "没有标签的一条", "第二条"), hot.items.map { it.word })
        val (_, paid, rebuttal, unlabelled) = hot.items
        assertNull(paid.rank)
        assertTrue(paid.isAd)
        // The short label is the one saying 商.
        assertEquals("商", paid.badge)
        assertEquals(0xFF00B7EEL, paid.badgeColor)
        assertEquals("辟谣", rebuttal.badge)
        assertEquals("荐", unlabelled.badge)
    }

    @Test
    fun `reads the pinned entries without their hash marks`() {
        val hot =
            parse(
                hotSearchJson(
                    hotgovs =
                        listOf(obj("word" to "#置顶的话题#", "name" to "#置顶的话题#", "icon_desc" to "热")),
                    hotgov = obj("word" to "#另一个#", "note" to "#另一个#"),
                )
            )

        val pinned = hot.pinned.single()
        assertEquals("置顶的话题", pinned.word)
        assertEquals("#置顶的话题#", pinned.query)
        assertNull(pinned.rank)
        assertNull(pinned.heat)
        assertEquals("热", pinned.badge)
    }

    @Test
    fun `falls back to the single pinned entry`() {
        assertEquals(
            "置顶的话题",
            parse(hotSearchJson(hotgov = obj("word" to "#置顶的话题#"))).pinned.single().word,
        )
    }

    @Test
    fun `tolerates missing lists and bad colours`() {
        val hot =
            HotSearch.fromJson(
                obj(
                    "realtime" to
                        listOf(
                            obj(
                                "word" to "一条",
                                "small_icon_desc" to "新",
                                "small_icon_desc_color" to "red",
                            ),
                            obj("num" to 5),
                        )
                )
            )

        assertTrue(hot.pinned.isEmpty())
        assertEquals("新", hot.items.single().badge)
        assertNull(hot.items.single().badgeColor)
    }

    // topic_test

    @Test
    fun `reads a topic - its lead, picture and all-time counts`() {
        val topic = Topic.tryFromJson(topicHeadsJson(name = "某个话题", summary = "话题导语"))!!

        assertEquals("某个话题", topic.name)
        assertEquals("话题导语", topic.summary)
        assertEquals("https://wx1.sinaimg.cn/thumb180/topic.jpg", topic.imageUrl)
        // Not the 24-hour counts.
        assertEquals(42262434L, topic.reads)
        assertEquals(9365L, topic.mentions)
        assertEquals(1997L, topic.authors)
    }

    @Test
    fun `an empty lead is none`() {
        assertNull(Topic.tryFromJson(topicHeadsJson(name = "某个话题"))!!.summary)
    }

    @Test
    fun `a word searched for is no topic, counts and all`() {
        assertNull(Topic.tryFromJson(topicHeadsJson()))
        assertNull(Topic.tryFromJson(null))
        assertNull(Topic.tryFromJson(obj()))
    }

    // notice_test

    @Test
    fun `unread notices mark the kinds with counts, missing ones being 0`() {
        val unread =
            UnreadNotices.fromJson(
                obj("cmt" to 2, "all_mention_status" to 1, "mention_cmt" to 0, "messages" to 3)
            )

        assertEquals(setOf(NoticeKind.Comments, NoticeKind.MentionStatuses), unread.kinds)
        assertTrue(unread.any)
        assertEquals(setOf(NoticeKind.MentionStatuses), unread.without(NoticeKind.Comments).kinds)
    }

    @Test
    fun `nothing counted is nothing unread`() {
        // Direct messages and the timeline aren't notifications here.
        val unread =
            UnreadNotices.fromJson(
                obj(
                    "dm_unread" to 4,
                    "status_24unread" to 100,
                    "remind_settings" to obj("msgbox" to 1),
                )
            )

        assertFalse(unread.any)
    }

    @Test
    fun `a comment notice carries the post and the comment it answers`() {
        val notice =
            CommentNotice.tryFromJson(
                commentNoticeJson(
                    id = 42,
                    text = "回复的内容",
                    replyTo = commentJson(id = 41, text = "我的评论"),
                )
            )!!

        assertEquals("42", notice.id)
        assertEquals("回复的内容", notice.comment.text)
        assertEquals("被评论的微博", notice.status?.text)
        assertEquals("我的评论", notice.replyTo?.text)
        assertNull(CommentNotice.tryFromJson(obj("text" to "没有 id")))
    }

    @Test
    fun `a like of a post, by its idStr`() {
        val like = LikeNotice.tryFromJson(likeNoticeJson(id = 9))!!

        assertEquals("9", like.id)
        assertEquals("点赞的人", like.user?.screenName)
        assertNotNull(like.createdAt)
        assertEquals("被赞的微博", like.status?.text)
        assertNull(like.comment)
        assertTrue(like.statusComplete)
    }

    @Test
    fun `a cut-down post still shows, but is marked so`() {
        val like = LikeNotice.tryFromJson(likeNoticeJson(cut = true))!!

        assertEquals("被赞的微博", like.status?.text)
        assertEquals("示例用户", like.status?.user?.screenName)
        assertFalse(like.statusComplete)
    }

    @Test
    fun `a like of a comment takes the post from the comment`() {
        val like =
            LikeNotice.tryFromJson(
                likeNoticeJson(
                    comment =
                        commentJson(id = 5, text = "被赞的评论") +
                            mapOf("status" to statusJson(id = 6, text = "评论所在的微博"))
                )
            )!!

        assertEquals("被赞的评论", like.comment?.text)
        assertEquals("6", like.status?.id)
    }
}

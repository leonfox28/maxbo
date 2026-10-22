package io.github.leonfox28.maxbo.data.model

import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentPicJson
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.plus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentTest {
    @Test
    fun `parses a comment with its thread preview`() {
        val c =
            Comment.tryFromJson(
                commentJson(
                    id = 6000000000000042,
                    text = "说得好",
                    likes = 7,
                    replyCount = 5,
                    replies =
                        listOf(
                            commentJson(
                                id = 2,
                                text = "回复<a href=/n/评论者 usercard=\"name=评论者\">@评论者</a>:同意",
                            )
                        ),
                )
            )!!

        assertEquals("6000000000000042", c.id)
        assertEquals("评论者", c.user?.screenName)
        assertEquals(Instant.parse("2026-10-12T05:20:00Z"), c.createdAt)
        assertEquals("来自上海", c.source)
        assertEquals("说得好", c.text)
        assertEquals(7, c.likesCount)
        assertEquals(5, c.replyCount)
        assertEquals("2", c.replies.single().id)
        assertTrue(c.replies.single().text.startsWith("回复"))
        assertTrue(c.pics.isEmpty())
    }

    @Test
    fun `reads attached pictures from url_struct`() {
        val c = Comment.tryFromJson(commentJson(urlStruct = listOf(commentPicJson("p1"))))!!

        val pic = c.pics.single()
        assertEquals("p1", pic.id)
        // No sizes: renditions stay smallest first, and the smallest is used.
        assertEquals("https://wx4.sinaimg.cn/thumbnail/p1.jpg", pic.variantFor(300f).url)
        assertEquals("https://wx4.sinaimg.cn/large/p1.jpg", pic.viewerVariant.url)
        assertEquals("https://wx4.sinaimg.cn/woriginal/p1.jpg", pic.largest?.url)
        // The entry still resolves its short link.
        assertEquals("http://t.cn/p1", c.urls.single().shortUrl)
    }

    @Test
    fun `skips the picture of the comment a reply answers`() {
        // Weibo copies the answered comment's url_struct entry into the reply,
        // without linking it from text_raw or counting it in pic_num.
        val copied =
            commentJson(urlStruct = listOf(commentPicJson("root"))) +
                mapOf("text_raw" to "回复", "pic_num" to 0)
        assertTrue(Comment.tryFromJson(copied)!!.pics.isEmpty())

        val ownToo =
            commentJson(urlStruct = listOf(commentPicJson("root"), commentPicJson("p2"))) +
                mapOf("text_raw" to "回复 http://t.cn/p2", "pic_num" to 1)
        assertEquals(listOf("p2"), Comment.tryFromJson(ownToo)!!.pics.map { it.id })
    }

    @Test
    fun `a comment just sent has its pictures by pid, not by link`() {
        val pid = "0000aaaaay1bbbbbbbbbbj0000000001"
        val c =
            Comment.tryFromJson(
                commentJson(
                    text =
                        "看<a href=/n/朋友 usercard=\"name=朋友\">@朋友</a> " +
                            "<a target=\"_blank\" href=\"https://t.cn/xyz\">" +
                            "<img class=\"icon-link\" src=\"https://h5.sinaimg.cn/l.png\">网页链接</a>"
                ) + mapOf("pic_num" to 1, "pic_ids" to listOf(pid))
            )!!

        val pic = c.pics.single()
        assertEquals(pid, pic.id)
        assertEquals("https://wx3.sinaimg.cn/thumbnail/$pid.jpg", pic.variantFor(300f).url)
        assertEquals("https://wx3.sinaimg.cn/large/$pid.jpg", pic.viewerVariant.url)
        assertEquals("https://wx3.sinaimg.cn/woriginal/$pid.jpg", pic.largest?.url)
        // The link to the picture goes; the mention stays.
        assertEquals("看<a href=/n/朋友 usercard=\"name=朋友\">@朋友</a>", c.text)
    }

    @Test
    fun `a pid says whether the picture is a GIF`() {
        val pid = "0000aaaaay1bbbbbbbbbbg0000000002"
        val c =
            Comment.tryFromJson(commentJson() + mapOf("pic_num" to 1, "pic_ids" to listOf(pid)))!!

        assertEquals(PicType.Gif, c.pics.single().type)
        assertEquals("https://wx4.sinaimg.cn/woriginal/$pid.gif", c.pics.single().largest?.url)
        // Older pids have another address scheme, not worked out yet.
        assertTrue(
            Comment.tryFromJson(
                    commentJson() + mapOf("pic_num" to 1, "pic_ids" to listOf("6a8b1d2fgw1e2abc"))
                )!!
                .pics
                .isEmpty()
        )
    }

    @Test
    fun `tolerates missing and mistyped fields`() {
        assertNull(Comment.tryFromJson(null))
        assertNull(Comment.tryFromJson(obj("text" to "no id")))

        val c =
            Comment.tryFromJson(
                obj(
                    "id" to 9,
                    "user" to emptyList<Any>(),
                    "like_counts" to "3",
                    "total_number" to null,
                    "comments" to "none",
                    "url_struct" to obj(),
                )
            )!!
        assertEquals("9", c.id)
        assertNull(c.user)
        assertEquals(3, c.likesCount)
        assertEquals(0, c.replyCount)
        assertTrue(c.replies.isEmpty())
        assertTrue(c.pics.isEmpty())
    }
}

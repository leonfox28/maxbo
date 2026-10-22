package io.github.leonfox28.maxbo.ui.widget.text

import io.github.leonfox28.maxbo.data.model.UrlEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusTextParserTest {
    /** A compact, comparable rendering of segments. */
    private fun describe(segments: List<TextSegment>) = segments.map { s ->
        when (s) {
            is TextSegment.Plain -> "text:${s.text}"
            is TextSegment.Emoticon -> "emoticon:${s.alt}@${s.url}"
            TextSegment.Expand -> "expand"
            is TextSegment.Link ->
                "link:${s.text}→" +
                    when (val link = s.link) {
                        is StatusLink.Mention -> "user:${link.screenName}"
                        is StatusLink.Topic -> "topic:${link.topic}"
                        is StatusLink.Image -> "image:${link.url}"
                        is StatusLink.Web -> "web:${link.url}"
                    } +
                    if (s.iconUrl == null) "" else " icon"
        }
    }

    @Test
    fun `plain text with line breaks and entities`() {
        assertEquals(
            listOf("text:第一行\n第二行 & <b>"),
            describe(parseStatusText("第一行<br />第二行 &amp; &lt;b&gt;")),
        )
    }

    @Test
    fun `mentions with unquoted href`() {
        assertEquals(
            listOf("text:你好", "link:@示例用户→user:示例用户", "text: 再见"),
            describe(parseStatusText("你好<a href=/n/示例用户 usercard=\"name=示例用户\">@示例用户</a> 再见")),
        )
    }

    @Test
    fun `topics link by their label`() {
        assertEquals(
            listOf("link:#示例话题#→topic:示例话题"),
            describe(
                parseStatusText(
                    "<a href=\"//s.weibo.com/weibo?q=%23示例话题%23\" target=\"_blank\">#示例话题#</a>"
                )
            ),
        )
    }

    @Test
    fun `emoticons become images with their alt text`() {
        assertEquals(
            listOf("text:好", "emoticon:[笑]@https://face.t.sinajs.cn/xiao.png"),
            describe(
                parseStatusText(
                    "好<img alt=\"[笑]\" title=\"[笑]\" src=\"//face.t.sinajs.cn/xiao.png\" />"
                )
            ),
        )
        // Without a usable src, the alt text stands in.
        assertEquals(listOf("text:[笑]"), describe(parseStatusText("<img alt=\"[笑]\" src=\"\">")))
    }

    @Test
    fun `short links resolve through url_struct and keep their icon`() {
        val urls =
            listOf(
                UrlEntity(
                    shortUrl = "http://t.cn/AbCd",
                    longUrl = "https://example.com/article",
                    title = "网页链接",
                )
            )
        assertEquals(
            listOf("text:看 ", "link:网页链接→web:https://example.com/article icon"),
            describe(
                parseStatusText(
                    "看 <a target=\"_blank\" href=\"http://t.cn/AbCd\"><img class=\"icon-link\" src=\"https://h5.sinaimg.cn/web.png\"/>网页链接</a>",
                    urls = urls,
                )
            ),
        )
        // An unknown short link opens as is.
        assertEquals(
            listOf("link:网页链接→web:http://t.cn/Zz"),
            describe(parseStatusText("<a href=\"http://t.cn/Zz\">网页链接</a>")),
        )
    }

    @Test
    fun `leaves out links to hidden pictures with their labels`() {
        assertEquals(
            listOf("text:图片评论 ", "link:查看图片→image:https://wx1.sinaimg.cn/large/p2.jpg"),
            describe(
                parseStatusText(
                    "图片评论 <a data-pid=p1 href=https://wx1.sinaimg.cn/large/p1.jpg>查看图片</a>" +
                        "<a data-pid=p2 href=https://wx1.sinaimg.cn/large/p2.jpg>查看图片</a>",
                    hiddenPictures = listOf("p1"),
                )
            ),
        )
    }

    @Test
    fun `picture links and app-only links`() {
        assertEquals(
            listOf("link:查看图片→image:https://wx1.sinaimg.cn/large/p1.jpg", "text:打开客户端"),
            describe(
                parseStatusText(
                    "<a target=\"_blank\" data-pid=\"p1\" href=\"https://wx1.sinaimg.cn/large/p1.jpg\">查看图片</a>" +
                        "<a href=\"sinaweibo://searchall?q=x\">打开客户端</a>"
                )
            ),
        )
    }

    @Test
    fun `a truncated post ends with expand`() {
        assertEquals(
            listOf("text:很长的内容...", "expand"),
            describe(parseStatusText("很长的内容...<span class=\"expand\">展开</span>")),
        )
    }

    @Test
    fun `unknown elements keep their text`() {
        assertEquals(listOf("text:粗体"), describe(parseStatusText("<b>粗</b><span>体</span>")))
    }

    @Test
    fun `plainStatusText flattens a body to one line`() {
        assertEquals(
            "看@某人 [doge] 第二行",
            plainStatusText(
                "看<a href=/n/某人 usercard=\"name=某人\">@某人</a> " +
                    "<img alt=\"[doge]\" src=\"https://face.t.sinajs.cn/doge.png\">" +
                    "<br>第二行<span class=\"expand\">展开</span>"
            ),
        )
    }
}

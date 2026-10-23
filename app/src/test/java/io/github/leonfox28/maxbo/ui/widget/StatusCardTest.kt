package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.mixedMediaFields
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.picJson
import io.github.leonfox28.maxbo.support.plus
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.support.userJson
import io.github.leonfox28.maxbo.support.videoPageInfo
import io.github.leonfox28.maxbo.support.without
import io.github.leonfox28.maxbo.ui.widget.status.StatusCard
import io.github.leonfox28.maxbo.ui.widget.status.StatusCardActions
import io.github.leonfox28.maxbo.ui.widget.text.StatusLink
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// "Pressing a count shows no ink" (Flutter) has no counterpart: the counts
// are clickable with `indication = null`.
@RunWith(RobolectricTestRunner::class)
class StatusCardTest {
    @get:Rule val compose = createComposeRule()

    private val card = mutableStateOf<Triple<Status, StatusCardActions, Boolean>?>(null)
    private val graph = testGraph(restored = signedIn)

    private fun show(
        json: JsonObject,
        actions: StatusCardActions = StatusCardActions(),
        detail: Boolean = false,
    ) {
        val status = Status.tryFromJson(json)!!
        if (card.value == null) {
            card.value = Triple(status, actions, detail)
            compose.setContent {
                TestHost(graph) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        card.value?.let { (status, actions, detail) ->
                            StatusCard(status, actions = actions, detail = detail)
                        }
                    }
                }
            }
        } else {
            card.value = Triple(status, actions, detail)
        }
        compose.waitForIdle()
    }

    private fun text(text: String, substring: Boolean = false) =
        compose.onNode(hasText(text, substring = substring), useUnmergedTree = true)

    // Taps on text land by position, which Robolectric's text layout gets
    // only roughly right: links are tapped through their semantics instead,
    // and the retweet through its box.

    @Test
    fun `shows author, text, meta and counts`() {
        show(statusJson(text = "你好，世界"))

        text("示例用户").assertExists()
        text("你好，世界").assertExists()
        text("来自 示例客户端", substring = true).assertExists()
        text("3").assertExists() // reposts
        text("1.2万").assertExists() // comments
        text("赞").assertExists() // no likes yet
        text("广告").assertDoesNotExist()
    }

    @Test
    fun `labels ads, pinned posts and posts not shown to everyone`() {
        show(statusJson(extra = mapOf("isAd" to true)))
        text("广告").assertExists()

        show(statusJson(extra = mapOf("isTop" to 1, "isAd" to true)))
        text("置顶").assertExists()
        text("广告").assertExists()

        show(statusJson(extra = mapOf("visible" to obj("type" to 1, "list_id" to 0))))
        text("仅自己可见").assertExists()

        show(statusJson(extra = mapOf("visible" to obj("type" to 0, "list_id" to 0))))
        text("公开").assertDoesNotExist()
    }

    @Test
    fun `says why a post by someone else is on a profile`() {
        val json =
            statusJson(
                user = userJson(id = 2002, name = "另一位用户"),
                extra =
                    mapOf(
                        "title" to
                            obj(
                                "text" to "他10-17赞过的微博(10)",
                                "structs" to
                                    listOf(
                                        obj(
                                            "name" to "他10-17赞过的微博(10)",
                                            "scheme" to
                                                "sinaweibo://cardlist?containerid=1001_-_mix",
                                        )
                                    ),
                            )
                    ),
            )
        show(json)
        val label = text("他10-17赞过的微博")
        // Atop the card, over its author.
        assertTrue(
            label.fetchSemanticsNode().boundsInRoot.bottom <=
                text("另一位用户").fetchSemanticsNode().boundsInRoot.top
        )

        // The detail page is about the post alone.
        show(json, detail = true)
        label.assertDoesNotExist()
    }

    @Test
    fun `offers to delete the signed-in user's posts only`() {
        val deleted = mutableListOf<String>()
        val actions = StatusCardActions(onDelete = { deleted += it.id })

        show(statusJson(id = 2, user = userJson(id = 8)), actions)
        compose.onNodeWithContentDescription("更多").assertDoesNotExist()

        show(statusJson(id = 3, user = userJson(id = 7)), actions)
        compose.onNodeWithContentDescription("更多").performClick()
        assertEquals(listOf("3"), deleted)
    }

    @Test
    fun `the avatar and the name report the author`() {
        val tapped = mutableListOf<String>()
        show(
            statusJson(user = userJson(id = 1001, name = "作者")),
            StatusCardActions(onUser = { tapped += it.id }),
        )

        compose.onNodeWithTag("avatar").performClick()
        text("作者").performClick()
        assertEquals(listOf("1001", "1001"), tapped)
    }

    @Test
    fun `shows the retweeted post with its author`() {
        show(
            statusJson(
                text = "转发理由",
                retweeted =
                    statusJson(id = 2, text = "原文内容", user = userJson(id = 2002, name = "原作者")),
            )
        )

        text("转发理由").assertExists()
        text("@原作者：原文内容").assertExists()
    }

    @Test
    fun `shows nine pictures at most, with the rest counted`() {
        show(statusJson(pics = (0 until 12).map { picJson("p$it") }))

        compose.onAllNodesWithTag("media").assertCountEquals(9)
        text("+3").assertExists()
    }

    @Test
    fun `shows a video cover with its length`() {
        var tapped = false
        show(
            statusJson(
                pageInfo =
                    obj(
                        "object_type" to "video",
                        "page_pic" to "https://wx1.sinaimg.cn/orj480/cover.jpg",
                        "media_info" to obj("duration" to 95),
                    )
            ),
            StatusCardActions(onVideo = { _, _ -> tapped = true }),
        )

        text("1:35").assertExists()
        compose.onNodeWithContentDescription("播放").performClick()
        assertTrue(tapped)
    }

    @Test
    fun `shows the pictures and videos of a retweeted mixed post`() {
        var video: Pair<Status, Int?>? = null
        var picture: Pair<List<PicInfo>, Int>? = null
        show(
            statusJson(
                text = "转发",
                retweeted =
                    statusJson(
                        id = 2,
                        text = "原微博",
                        extra =
                            mixedMediaFields(
                                listOf(
                                    videoPageInfo(name = "first"),
                                    picJson("p1"),
                                    videoPageInfo(name = "second"),
                                )
                            ),
                    ),
            ),
            StatusCardActions(
                onVideo = { status, index -> video = status to index },
                onPicture = { pics, index -> picture = pics to index },
            ),
        )

        compose.onAllNodesWithText("0:49", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithContentDescription("播放")[1].performClick()
        assertEquals("2", video?.first?.id)
        assertEquals(1, video?.second)

        compose.onAllNodesWithTag("media")[1].performClick()
        assertEquals(listOf("p1"), picture?.first?.map { it.id })
        assertEquals(0, picture?.second)
    }

    @Test
    fun `a retweet shows its source video once`() {
        // Weibo copies the source's page_info onto the retweet as well.
        val video =
            obj(
                "object_type" to "video",
                "page_pic" to "https://wx1.sinaimg.cn/orj480/cover.jpg",
                "media_info" to obj("duration" to 60),
            )
        show(
            statusJson(
                text = "转发",
                pageInfo = video,
                retweeted = statusJson(id = 2, text = "原视频", pageInfo = video),
            )
        )

        compose.onAllNodesWithText("1:00", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `links in the text report what was tapped`() {
        val tapped = mutableListOf<StatusLink>()
        show(
            statusJson(
                text =
                    "看<a href=/n/朋友 usercard=\"name=朋友\">@朋友</a>的" +
                        "<a href=\"//s.weibo.com/weibo?q=%23话题%23\">#话题#</a>"
            ),
            StatusCardActions(onLink = { tapped += it }),
        )

        val body = text("看@朋友的#话题#")
        body.performFirstLinkClick { it.start == 1 }
        body.performFirstLinkClick { it.start > 1 }

        assertEquals(listOf(StatusLink.Mention("朋友"), StatusLink.Topic("话题")), tapped)
    }

    @Test
    fun `tapping the card or its retweet opens that post`() {
        val opened = mutableListOf<String>()
        show(
            statusJson(id = 1, text = "转发理由", retweeted = statusJson(id = 2, text = "原文内容")),
            StatusCardActions(onOpen = { opened += it.id }),
        )

        text("转发理由").performClick()
        compose.onNodeWithTag("retweet").performClick()

        assertEquals(listOf("1", "2"), opened)
    }

    @Test
    fun `the repost count reposts the post, if anything does`() {
        val opened = mutableListOf<String>()
        val reposted = mutableListOf<String>()
        show(
            statusJson(id = 1),
            StatusCardActions(onOpen = { opened += it.id }, onRepost = { reposted += it.id }),
        )

        text("3").performClick()
        assertEquals(listOf("1"), reposted)
        assertTrue(opened.isEmpty())

        show(statusJson(id = 1), StatusCardActions(onOpen = { opened += it.id }))
        text("3").performClick()
        assertEquals(listOf("1"), opened)
    }

    @Test
    fun `a deleted retweet does not open`() {
        val opened = mutableListOf<String>()
        show(
            statusJson(
                id = 1,
                text = "转发理由",
                retweeted = obj("idstr" to "9", "text" to "抱歉，此微博已被作者删除。"),
            ),
            StatusCardActions(onOpen = { opened += it.id }),
        )

        text("抱歉，此微博已被作者删除。").performClick()

        assertEquals(listOf("1"), opened) // The tap falls through to the outer card.
    }

    @Test
    fun `a retweet source without its author still opens`() {
        val opened = mutableListOf<String>()
        // As in `statuses/mentions`: no `user`, the text only in `text_raw`.
        val source =
            statusJson(id = 9, text = "", extra = mapOf("text_raw" to "原文开头")).without("user") +
                mapOf("isLongText" to true)
        show(
            statusJson(id = 1, text = "转发理由", retweeted = source),
            StatusCardActions(
                onOpen = { opened += it.id },
                onExpand = { opened += "expand ${it.id}" },
            ),
        )

        compose.onNodeWithTag("retweet").performClick()
        text("原文开头", substring = true).performFirstLinkClick()

        assertEquals(listOf("9", "expand 9"), opened)
    }

    @Test
    fun `a mentions retweet source shows the author from its card`() {
        val source =
            statusJson(id = 9, text = "", extra = mapOf("text_raw" to "原文开头")).without("user")
        show(
            statusJson(
                id = 1,
                text = "转发理由",
                retweeted = source,
                pageInfo =
                    obj(
                        "object_type" to "meaasgeObjType",
                        "content1" to "@原作者",
                        "uidPageInfo" to "42",
                        "mblogid" to "Abc9",
                    ),
            )
        )

        text("@原作者", substring = true).assertExists()
        text("原文开头", substring = true).assertExists()
    }

    @Test
    fun `in detail mode the card is inert and has no counts`() {
        val opened = mutableListOf<String>()
        show(
            statusJson(text = "正文"),
            StatusCardActions(onOpen = { opened += it.id }),
            detail = true,
        )

        text("正文").performClick()

        assertTrue(opened.isEmpty())
        text("1.2万").assertDoesNotExist()
    }

    @Test
    fun `tapping a picture reports the pictures and index`() {
        var tapped: Pair<List<PicInfo>, Int>? = null
        show(
            statusJson(pics = listOf(picJson("a"), picJson("b"), picJson("c"))),
            StatusCardActions(onPicture = { pics, i -> tapped = pics to i }),
        )

        compose.onAllNodesWithTag("media")[1].performClick()

        assertEquals(listOf("a", "b", "c"), tapped?.first?.map { it.id })
        assertEquals(1, tapped?.second)
    }
}

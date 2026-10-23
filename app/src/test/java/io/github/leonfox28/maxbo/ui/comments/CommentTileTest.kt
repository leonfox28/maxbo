package io.github.leonfox28.maxbo.ui.comments

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentPicJson
import io.github.leonfox28.maxbo.support.userJson
import io.github.leonfox28.maxbo.ui.widget.status.StatusCardActions
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// "Pressing the like button shows no ink" (Flutter) has no counterpart: the
// button is clickable with `indication = null`.
@RunWith(RobolectricTestRunner::class)
class CommentTileTest {
    @get:Rule val compose = createComposeRule()

    private class Shown(
        val comment: Comment,
        val authorId: String?,
        val actions: StatusCardActions,
        val onThread: (() -> Unit)?,
    )

    private val shown = mutableStateOf<Shown?>(null)

    private fun show(
        json: JsonObject,
        authorId: String? = null,
        actions: StatusCardActions = StatusCardActions(),
        onThread: (() -> Unit)? = null,
    ) {
        val first = shown.value == null
        shown.value = Shown(Comment.tryFromJson(json)!!, authorId, actions, onThread)
        if (first) {
            compose.setContent {
                TestHost {
                    shown.value?.let {
                        CommentTile(
                            it.comment,
                            authorId = it.authorId,
                            actions = it.actions,
                            onThread = it.onThread,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun text(text: String, substring: Boolean = false) =
        compose.onNode(hasText(text, substring = substring), useUnmergedTree = true)

    @Test
    fun `shows the author, text, region and likes`() {
        show(commentJson(text = "说得好", likes = 12345))

        text("评论者").assertExists()
        text("说得好").assertExists()
        text("来自上海", substring = true).assertExists()
        text("1.2万").assertExists()
        text("博主").assertDoesNotExist()
    }

    @Test
    fun `labels comments by the status author`() {
        show(commentJson(user = userJson(id = 1001, name = "博主本人")), authorId = "1001")

        text("博主").assertExists()
    }

    @Test
    fun `previews the thread and opens it`() {
        var opened = 0
        show(
            commentJson(
                replyCount = 5,
                replies =
                    listOf(
                        commentJson(
                            id = 2,
                            text = "第一条回复",
                            user = userJson(id = 2002, name = "回复者"),
                        )
                    ),
            ),
            onThread = { opened++ },
        )

        text("@回复者：第一条回复").assertExists()
        text("共5条回复 >").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `without onThread there is no preview`() {
        show(commentJson(replyCount = 5))

        text("共5条回复 >").assertDoesNotExist()
    }

    @Test
    fun `tapping a picture opens the comment pictures`() {
        var tapped: List<PicInfo>? = null
        show(
            commentJson(urlStruct = listOf(commentPicJson("p1"))),
            actions = StatusCardActions(onPicture = { pics, _ -> tapped = pics }),
        )

        compose.onNodeWithTag("media").performClick()

        assertEquals("p1", tapped?.single()?.id)
    }

    @Test
    fun `leaves out the text link to a picture shown below`() {
        // Like Weibo's, a picture reply also links its picture from text.
        val reply =
            commentJson(
                id = 2,
                text =
                    "图片回复 <a target=_blank data-pid=p2 href=https://wx3.sinaimg.cn/large/p2.jpg>查看图片</a>",
                urlStruct = listOf(commentPicJson("p2")),
            )
        show(reply)

        text("图片回复 ").assertExists()
        text("查看图片", substring = true).assertDoesNotExist()
        compose.onAllNodesWithTag("media").assertCountEquals(1)

        // The thread preview shows no pictures, so it keeps the link.
        show(commentJson(replyCount = 1, replies = listOf(reply)), onThread = {})

        text("查看图片", substring = true).assertExists()
    }
}

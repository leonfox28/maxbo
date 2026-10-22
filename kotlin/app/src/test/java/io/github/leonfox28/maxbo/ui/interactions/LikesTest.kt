package io.github.leonfox28.maxbo.ui.interactions

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.WithSnackbars
import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.form
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.ui.comments.CommentTile
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.widget.status.StatusCard
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LikesTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val graph = testGraph(restored = signedIn, web = web)
    private val likes = graph.likes
    private val status = LikeTarget(LikeKind.Status, "42")
    private val unliked = LikeState(liked = false, count = 5)

    @After fun tearDown() = web.close()

    private fun paths() = web.requests.map { it.url.encodedPath }

    private fun shown(loaded: LikeState) =
        LikesController.resolve(likes.changes.value[status], loaded)

    private fun answer(body: String) {
        web.respond = { json(body) }
    }

    @Test
    fun `shows a like at once, and keeps it once Weibo agrees`() = runTest {
        val held = CountDownLatch(1)
        web.respond = {
            held.await(10, TimeUnit.SECONDS)
            json("""{"ok":1,"attitude":"heart"}""")
        }

        val toggling = launch { likes.toggle(status, unliked) }
        runCurrent()
        assertEquals(LikeState(liked = true, count = 6), shown(unliked))
        assertTrue(likes.changes.value[status]!!.pending)

        held.countDown()
        toggling.join()
        assertEquals(LikeState(liked = true, count = 6), shown(unliked))
        assertFalse(likes.changes.value[status]!!.pending)
        assertEquals(listOf("/ajax/statuses/setLike"), paths())
    }

    @Test
    fun `takes the like back when Weibo refuses it`() = runTest {
        answer("""{"ok":1,"attitude":"heart"}""")
        likes.toggle(status, unliked)

        answer("""{"ok":1,"result":false}""")
        val error = runCatching { likes.toggle(status, unliked) }.exceptionOrNull()

        assertTrue("got $error", error is WeiboApiException)
        assertEquals("/ajax/statuses/cancelLike", paths().last())
        assertEquals(LikeState(liked = true, count = 6), shown(unliked))
    }

    @Test
    fun `ignores toggles while one is on its way`() = runTest {
        val held = CountDownLatch(1)
        web.respond = {
            held.await(10, TimeUnit.SECONDS)
            json("""{"ok":1}""")
        }

        val first = launch { likes.toggle(status, unliked) }
        runCurrent()
        likes.toggle(status, unliked)
        held.countDown()
        first.join()

        assertEquals(1, web.requests.size)
        assertTrue(shown(unliked).liked)
    }

    @Test
    fun `newer data wins over a change made on older data`() = runTest {
        answer("""{"ok":1}""")
        likes.toggle(status, unliked)

        // Reloaded: liked, and by others meanwhile.
        val reloaded = LikeState(liked = true, count = 9)
        assertEquals(reloaded, shown(reloaded))
        // A list still holding the old data shows the change.
        assertEquals(LikeState(liked = true, count = 6), shown(unliked))

        answer("""{"ok":1,"result":true}""")
        likes.toggle(status, reloaded)
        assertEquals(LikeState(liked = false, count = 8), shown(reloaded))
        // The old data says unliked, which is right again.
        assertEquals(unliked, shown(unliked))
    }

    @Test
    fun `comments use the comment endpoints`() = runTest {
        answer("""{"ok":1,"result":true}""")

        likes.toggle(LikeTarget(LikeKind.Comment, "9"), unliked)

        assertEquals(listOf("/ajax/statuses/updateLike"), paths())
    }

    @Test
    fun `signing out forgets them, for the next account`() = runTest {
        answer("""{"ok":1}""")
        likes.toggle(status, unliked)

        graph.session.logout()
        yield()

        assertEquals(unliked, shown(unliked))
    }

    /** The same status, as two lists would show it. */
    private fun showCards(navigator: Navigator = Navigator()) {
        val card = Status.tryFromJson(statusJson(id = 42, extra = mapOf("attitudes_count" to 5)))!!
        compose.setContent {
            TestHost(graph, navigator) {
                WithSnackbars {
                    Column(Modifier) { repeat(2) { StatusCard(card) } }
                }
            }
        }
    }

    @Test
    fun `liking shows on every card of the status`() {
        answer("""{"ok":1,"attitude":"heart"}""")
        showCards()
        compose.onAllNodesWithText("5").assertCountEquals(2)

        compose.onAllNodesWithText("5")[0].performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithText("6").fetchSemanticsNodes().size == 2 }
        assertEquals(listOf("/ajax/statuses/setLike"), paths())
    }

    @Test
    fun `a refused like is taken back and explained`() {
        answer("""{"ok":0,"msg":"操作过于频繁"}""")
        showCards()

        compose.onAllNodesWithText("5")[0].performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("操作过于频繁").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("5").assertCountEquals(2)
    }

    @Test
    fun `a security check is offered, not taken`() {
        answer("""{"ok":-100,"url":"https://security.weibo.com/captcha/geetest?x=1"}""")
        val navigator = Navigator()
        showCards(navigator)

        compose.onAllNodesWithText("5")[0].performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("需要先完成微博的安全验证").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("去验证").performClick()
        compose.waitForIdle()

        val opened = navigator.backStack.last() as Route.Web
        assertTrue(opened.url, opened.url.contains("/captcha/geetest"))
        // The user likes again themselves once through.
        assertEquals(1, web.requests.size)
    }

    @Test
    fun `a comment's like button likes it, not replies`() {
        answer("""{"ok":1,"result":true}""")
        var tapped = 0
        val comment = Comment.tryFromJson(commentJson(id = 9, likes = 3))!!
        compose.setContent { TestHost(graph) { CommentTile(comment, onClick = { tapped++ }) } }

        compose.onNodeWithText("3").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("4").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("/ajax/statuses/updateLike"), paths())
        assertEquals("9", web.requests.single().form["object_id"])
        assertEquals(0, tapped)
    }
}

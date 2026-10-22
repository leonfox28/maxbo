package io.github.leonfox28.maxbo.ui.comments

import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.core.paging.PagedState
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.CommentOrder
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentsPageJson
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.testGraph
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommentsControllerTest {
    private val web = FakeWeibo()

    @After fun tearDown() = web.close()

    private fun TestScope.comments(): CommentsController =
        CommentsController(
            testGraph(restored = signedIn, web = web, scope = backgroundScope).api,
            statusId = "42",
            authorId = "1001",
            order = CommentOrder.Hot,
            scope = backgroundScope,
        )

    private suspend fun CommentsController.loaded(): PagedState<Comment> {
        start()
        return (state.first { it !is ListLoad.Loading } as ListLoad.Ready).list
    }

    private val CommentsController.list: PagedState<Comment>
        get() = (state.value as ListLoad.Ready).list

    private fun CommentsController.ids() = list.items.map { it.id }

    private fun page(vararg ids: Long, maxId: Long = 0, total: Int? = null) =
        json(
            commentsPageJson(ids.map { commentJson(id = it) }, maxId = maxId, total = total)
                .toString()
        )

    @Test
    fun `loads more from the cursor, skips repeats, stops at the end`() = runTest {
        web.respond = { page(1, 2, maxId = 50) }
        val comments = comments()
        comments.loaded()
        assertEquals(listOf("1", "2"), comments.ids())

        web.respond = { page(2, 3) }
        comments.loadMore()

        assertEquals("50", web.requests.last().url.queryParameter("max_id"))
        assertEquals(listOf("1", "2", "3"), comments.ids())
        assertFalse(comments.list.hasMore)

        comments.loadMore()
        assertEquals(2, web.requests.size)
    }

    @Test
    fun `keeps the list when loading more fails, and can retry`() = runTest {
        web.respond = { page(1, maxId = 50) }
        val comments = comments()
        comments.loaded()

        web.respond = { json("""{"ok":0,"message":"出错了"}""") }
        comments.loadMore()
        assertEquals(1, comments.list.items.size)
        assertTrue(comments.list.loadMoreError is WeiboApiException)
        assertEquals("出错了", comments.list.loadMoreError?.message)

        web.respond = { page(2) }
        comments.loadMore()
        assertEquals(listOf("1", "2"), comments.ids())
        assertNull(comments.list.loadMoreError)
    }

    @Test
    fun `drops a page that arrives after a refresh`() = runTest {
        web.respond = { page(1, maxId = 50) }
        val comments = comments()
        comments.loaded()

        val slowPage = CountDownLatch(1)
        val asked = CountDownLatch(1)
        web.respond = {
            if (it.url.queryParameter("max_id") == "50") {
                asked.countDown()
                slowPage.await(10, TimeUnit.SECONDS)
                page(2)
            } else {
                page(9)
            }
        }
        val loading = async { comments.loadMore() }
        runCurrent()
        withContext(Dispatchers.IO) { asked.await(10, TimeUnit.SECONDS) }

        comments.refresh()
        slowPage.countDown()
        loading.await()

        assertEquals(listOf("9"), comments.ids())
    }

    @Test
    fun `the user's comments go atop the list, deleted ones out`() = runTest {
        web.respond = { page(1, 2, total = 10) }
        val comments = comments()
        comments.loaded()

        comments.prepend(Comment.tryFromJson(commentJson(id = 3))!!)
        assertEquals(listOf("3", "1", "2"), comments.ids())
        assertEquals(11, comments.list.total)

        comments.remove("1")
        comments.remove("404")
        assertEquals(listOf("3", "2"), comments.ids())
        assertEquals(10, comments.list.total)

        comments.replace("2") { it.copy(replyCount = 5) }
        assertEquals(5, comments.list.items.last().replyCount)
    }

    @Test
    fun `a comment added while a page loads stays`() = runTest {
        web.respond = { page(1, maxId = 50) }
        val comments = comments()
        comments.loaded()

        val slowPage = CountDownLatch(1)
        val asked = CountDownLatch(1)
        web.respond = {
            asked.countDown()
            slowPage.await(10, TimeUnit.SECONDS)
            page(2)
        }
        val loading = async { comments.loadMore() }
        runCurrent()
        withContext(Dispatchers.IO) { asked.await(10, TimeUnit.SECONDS) }

        comments.prepend(Comment.tryFromJson(commentJson(id = 3))!!)
        slowPage.countDown()
        loading.await()

        assertEquals(listOf("3", "1", "2"), comments.ids())
        assertFalse(comments.list.loadingMore)
    }
}

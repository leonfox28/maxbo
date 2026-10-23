package io.github.leonfox28.maxbo.ui.timeline

import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.core.paging.PagedState
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.core.session.StoredSession
import io.github.leonfox28.maxbo.data.model.Account
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.feedJson
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.statusJson
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
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val session = StoredSession(Account(uid = "42"), mapOf("weibo.com" to mapOf("SUB" to "x")))

@RunWith(RobolectricTestRunner::class)
class TimelineControllerTest {
    private val web = FakeWeibo()

    @After fun tearDown() = web.close()

    private fun TestScope.graph(restored: StoredSession? = session): AppGraph =
        testGraph(restored = restored, web = web, scope = backgroundScope)

    /** The timeline once its first page is in. */
    private suspend fun AppGraph.loaded(): PagedState<Status> {
        timeline.start()
        return (timeline.state.first { it !is ListLoad.Loading } as ListLoad.Ready).list
    }

    private val AppGraph.list: PagedState<Status>
        get() = (timeline.state.value as ListLoad.Ready).list

    private fun AppGraph.ids() = list.items.map { it.id }

    private val RecordedRequest.maxId: String?
        get() = url.queryParameter("max_id")

    @Test
    fun `loads the newest page of 最新微博`() = runTest {
        web.respond = {
            json(feedJson(listOf(statusJson(id = 1), statusJson(id = 2)), maxId = 100).toString())
        }
        val graph = graph()

        val timeline = graph.loaded()

        assertEquals(listOf("1", "2"), timeline.items.map { it.id })
        assertTrue(timeline.hasMore)
        val url = web.requests.single().url
        assertEquals("/ajax/feed/friendstimeline", url.encodedPath)
        assertEquals("1100042", url.queryParameter("list_id"))
        assertEquals("0", url.queryParameter("since_id"))
        assertNull(url.queryParameter("max_id"))
    }

    @Test
    fun `signed out - empty, without requests`() = runTest {
        val graph = graph(restored = null)

        assertTrue(graph.loaded().items.isEmpty())
        assertTrue(web.requests.isEmpty())
    }

    @Test
    fun `loadMore continues from max_id, skips repeats and stops at the end`() = runTest {
        web.respond = {
            json(
                (if (it.maxId == null)
                        feedJson(listOf(statusJson(id = 3), statusJson(id = 2)), maxId = 100)
                    else feedJson(listOf(statusJson(id = 2), statusJson(id = 1))))
                    .toString()
            )
        }
        val graph = graph()
        graph.loaded()

        graph.timeline.loadMore()

        assertEquals(listOf("3", "2", "1"), graph.ids())
        assertEquals("100", web.requests.last().maxId)
        assertFalse(graph.list.hasMore)

        graph.timeline.loadMore()
        assertEquals(2, web.requests.size)
    }

    @Test
    fun `a failed loadMore is kept in state until retried`() = runTest {
        var fail = true
        web.respond = {
            when {
                it.maxId == null ->
                    json(feedJson(listOf(statusJson(id = 2)), maxId = 100).toString())
                fail -> json("""{"ok":0,"message":"服务繁忙"}""")
                else -> json(feedJson(listOf(statusJson(id = 1))).toString())
            }
        }
        val graph = graph()
        graph.loaded()

        graph.timeline.loadMore()

        assertEquals("服务繁忙", graph.list.loadMoreError?.message)
        assertFalse(graph.list.loadingMore)
        assertEquals(listOf("2"), graph.ids())

        fail = false
        graph.timeline.loadMore()

        assertNull(graph.list.loadMoreError)
        assertEquals(listOf("2", "1"), graph.ids())
    }

    @Test
    fun `refresh replaces the list, a failed refresh keeps it and throws`() = runTest {
        var page = listOf(statusJson(id = 1))
        web.respond = { json(feedJson(page).toString()) }
        val graph = graph()
        graph.loaded()

        page = listOf(statusJson(id = 2), statusJson(id = 1))
        graph.timeline.refresh()
        assertEquals(listOf("2", "1"), graph.ids())

        web.respond = { json("""{"ok":0,"message":"服务繁忙"}""", status = 500) }
        val failure = runCatching { graph.timeline.refresh() }.exceptionOrNull()
        assertTrue(failure is WeiboApiException)
        assertEquals(listOf("2", "1"), graph.ids())
    }

    @Test
    fun `a page loaded before a refresh lands is dropped`() = runTest {
        val olderPage = CountDownLatch(1)
        web.respond = {
            if (it.maxId == null) json(feedJson(listOf(statusJson(id = 5)), maxId = 100).toString())
            else {
                olderPage.await(10, TimeUnit.SECONDS)
                json(feedJson(listOf(statusJson(id = 1))).toString())
            }
        }
        val graph = graph()
        graph.loaded()

        val loading = async { graph.timeline.loadMore() }
        runCurrent()
        assertTrue(graph.list.loadingMore)
        graph.timeline.refresh()
        olderPage.countDown()
        loading.await()

        assertEquals(listOf("5"), graph.ids())
        assertFalse(graph.list.loadingMore)
    }

    @Test
    fun `a session rejected while loading more signs out`() = runTest {
        web.respond = {
            if (it.maxId == null) json(feedJson(listOf(statusJson(id = 2)), maxId = 100).toString())
            else json("""{"ok":-100,"url":"https://passport.weibo.com/"}""")
        }
        val graph = graph()
        graph.loaded()

        graph.timeline.loadMore()
        runCurrent()

        assertEquals(SessionState.SignedOut(expired = true), graph.session.state.value)
        assertTrue(graph.loaded().items.isEmpty())
    }

    @Test
    fun `posts the user writes or deletes go in and out`() = runTest {
        web.respond = { json(feedJson(listOf(statusJson(id = 1), statusJson(id = 2))).toString()) }
        val graph = graph()
        graph.loaded()

        graph.timeline.prepend(Status.tryFromJson(statusJson(id = 3))!!)
        assertEquals(listOf("3", "1", "2"), graph.ids())
        graph.timeline.remove("1")
        assertEquals(listOf("3", "2"), graph.ids())
    }

    @Test
    fun `a page loaded while the user posts follows the post`() = runTest {
        val olderPage = CountDownLatch(1)
        val olderAsked = CountDownLatch(1)
        web.respond = {
            if (it.maxId == null) json(feedJson(listOf(statusJson(id = 5)), maxId = 100).toString())
            else {
                olderAsked.countDown()
                olderPage.await(10, TimeUnit.SECONDS)
                json(feedJson(listOf(statusJson(id = 1))).toString())
            }
        }
        val graph = graph()
        graph.loaded()

        val loading = async { graph.timeline.loadMore() }
        runCurrent()
        withContext(Dispatchers.IO) { olderAsked.await(10, TimeUnit.SECONDS) }
        graph.timeline.prepend(Status.tryFromJson(statusJson(id = 9))!!)
        olderPage.countDown()
        loading.await()

        assertEquals(listOf("9", "5", "1"), graph.ids())
        assertFalse(graph.list.loadingMore)
    }
}

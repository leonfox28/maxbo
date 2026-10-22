package io.github.leonfox28.maxbo.data.api

import io.github.leonfox28.maxbo.core.net.SessionExpiredException
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.data.model.Account
import io.github.leonfox28.maxbo.data.model.CommentOrder
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.commentJson
import io.github.leonfox28.maxbo.support.commentsPageJson
import io.github.leonfox28.maxbo.support.form
import io.github.leonfox28.maxbo.support.homePage
import io.github.leonfox28.maxbo.support.html
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.plus
import io.github.leonfox28.maxbo.support.redirect
import io.github.leonfox28.maxbo.support.repostsPageJson
import io.github.leonfox28.maxbo.support.statusJson
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The endpoints themselves come over with their features (K2–K5); these are
// the requests every one of them is made of.
@RunWith(RobolectricTestRunner::class)
class WeiboApiTest {
    private val web = FakeWeibo()

    @After fun tearDown() = web.close()

    private suspend inline fun <reified T : Throwable> failure(block: () -> Unit): T {
        val e = runCatching { block() }.exceptionOrNull()
        assertTrue("got $e", e is T)
        return e as T
    }

    @Test
    fun `getJson sends its query, leaving out what is null`() = runTest {
        web.respond = { json("""{"ok":1}""") }

        web.api().getJson("https://weibo.com/ajax/x", mapOf("a" to 1, "b" to null, "c" to "中"))

        val url = web.requests.single().url
        assertEquals("/ajax/x", url.encodedPath)
        assertEquals(setOf("a", "c"), url.queryParameterNames)
        assertEquals("中", url.queryParameter("c"))
    }

    @Test
    fun `postForm posts forms with the XSRF token, as weibo_com does`() = runTest {
        web.respond = { json("""{"ok":1,"attitude":"heart"}""") }
        val api = web.api(mapOf("weibo.com" to mapOf("SUB" to "x", "XSRF-TOKEN" to "tok")))

        api.postForm(
            "https://weibo.com/ajax/statuses/setLike",
            mapOf("id" to "42", "empty" to null),
        )

        val request = web.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/ajax/statuses/setLike", request.url.encodedPath)
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        assertEquals("tok", request.headers["X-XSRF-TOKEN"])
        assertEquals("https://weibo.com", request.headers["Origin"])
        assertEquals(mapOf("id" to "42"), request.form)
    }

    @Test
    fun `an answer that says ok but has an error failed`() = runTest {
        web.respond = { json("""{"ok":1,"error":"评论太频繁"}""") }

        val e =
            failure<WeiboApiException> {
                web.api().postForm("https://weibo.com/ajax/comments/create", emptyMap())
            }

        assertEquals("评论太频繁", e.message)
    }

    @Test
    fun `fetchCurrentAccount reads the user weibo_com's page embeds`() = runTest {
        web.respond = { homePage(uid = 42) }

        assertEquals(Account("42", "测试用户"), web.api().fetchCurrentAccount())
        assertTrue(web.requests.single().headers["Accept"].orEmpty().startsWith("text/html"))
    }

    @Test
    fun `fetchCurrentAccount turns visitors away`() = runTest {
        web.respond = { homePage(uid = 0) }
        failure<SessionExpiredException> { web.api().fetchCurrentAccount() }

        // weibo.com sends a lapsed session to passport.
        web.respond = {
            if (it.url.host == "weibo.com") redirect("https://passport.weibo.com/visitor/visitor")
            else html("<html></html>")
        }
        failure<SessionExpiredException> { web.api().fetchCurrentAccount() }
    }

    @Test
    fun `fetchCurrentAccount reports a page without CONFIG`() = runTest {
        web.respond = { html("<html></html>") }

        val e = failure<WeiboApiException> { web.api().fetchCurrentAccount() }

        assertEquals("无法识别当前登录的账号", e.message)
    }

    private fun query() = web.requests.last().url

    @Test
    fun `fetchStatus asks for the full text of long posts`() = runTest {
        web.respond = { json((obj("ok" to 1) + statusJson(id = 42, text = "完整的长文")).toString()) }

        val status = web.api().fetchStatus("42")

        assertEquals("完整的长文", status.text)
        assertEquals("/ajax/statuses/show", query().encodedPath)
        assertEquals("42", query().queryParameter("id"))
        assertEquals("true", query().queryParameter("isGetLongText"))
        assertEquals(setOf("id", "isGetLongText"), query().queryParameterNames)
    }

    @Test
    fun `fetchStatus reports a response without a status`() = runTest {
        web.respond = { json("""{"ok":1}""") }

        failure<WeiboApiException> { web.api().fetchStatus("42") }
    }

    @Test
    fun `fetchComments sends the order and author, and continues from max_id`() = runTest {
        web.respond = {
            json(commentsPageJson(listOf(commentJson(id = 1)), maxId = 777, total = 99).toString())
        }
        val api = web.api()

        val first = api.fetchComments(statusId = "42", authorId = "1001", order = CommentOrder.Hot)
        assertEquals("/ajax/statuses/buildComments", query().encodedPath)
        assertEquals("42", query().queryParameter("id"))
        assertEquals("1001", query().queryParameter("uid"))
        assertEquals("0", query().queryParameter("flow"))
        assertEquals("0", query().queryParameter("fetch_level"))
        assertNull(query().queryParameter("max_id"))
        assertEquals("1", first.items.single().id)
        assertEquals("777", first.nextCursor)
        assertEquals(99, first.total)

        web.respond = { json(commentsPageJson(listOf(commentJson(id = 2))).toString()) }
        val last = api.fetchComments("42", "1001", CommentOrder.Time, maxId = first.nextCursor)
        assertEquals("1", query().queryParameter("flow"))
        assertEquals("777", query().queryParameter("max_id"))
        assertNull(last.nextCursor)
    }

    @Test
    fun `fetchComments - an empty page ends the list whatever max_id says`() = runTest {
        web.respond = { json(commentsPageJson(emptyList(), maxId = 5).toString()) }

        assertNull(web.api().fetchComments("42", "1001", CommentOrder.Hot).nextCursor)
    }

    @Test
    fun `fetchReplies lists the thread of a comment`() = runTest {
        web.respond = { json(commentsPageJson(listOf(commentJson(id = 3))).toString()) }

        val page = web.api().fetchReplies(commentId = "600", authorId = "1001")

        assertEquals("600", query().queryParameter("id"))
        assertEquals("1", query().queryParameter("fetch_level"))
        assertEquals("0", query().queryParameter("max_id"))
        assertEquals("3", page.items.single().id)
        assertNull(page.nextCursor)
    }

    @Test
    fun `fetchReposts pages until max_page`() = runTest {
        web.respond = {
            json(repostsPageJson(listOf(statusJson(id = 8)), maxPage = 2, total = 30).toString())
        }
        val api = web.api()

        val first = api.fetchReposts(statusId = "42")
        assertEquals("/ajax/statuses/repostTimeline", query().encodedPath)
        assertEquals("42", query().queryParameter("id"))
        assertEquals("1", query().queryParameter("page"))
        assertEquals("8", first.items.single().id)
        assertEquals("2", first.nextCursor)
        assertEquals(30, first.total)

        val second = api.fetchReposts(statusId = "42", page = 2)
        assertEquals("2", query().queryParameter("page"))
        assertNull(second.nextCursor)
    }
}

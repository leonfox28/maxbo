package io.github.leonfox28.maxbo.data.api

import io.github.leonfox28.maxbo.core.net.SessionExpiredException
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.data.model.Account
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.form
import io.github.leonfox28.maxbo.support.homePage
import io.github.leonfox28.maxbo.support.html
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.redirect
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
}

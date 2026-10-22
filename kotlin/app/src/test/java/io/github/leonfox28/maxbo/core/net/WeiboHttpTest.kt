package io.github.leonfox28.maxbo.core.net

import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.host
import io.github.leonfox28.maxbo.support.html
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.redirect
import kotlinx.coroutines.test.runTest
import okhttp3.FormBody
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeiboHttpTest {
    private val web = FakeWeibo()
    private val jar =
        WeiboCookieJar(mapOf("weibo.com" to mapOf("SUB" to "sub", "XSRF-TOKEN" to "token")))
    private val http = web.http(jar)

    init {
        web.respond = {
            json("""{"ok":1}""", headers = arrayOf("Set-Cookie" to "XSRF-TOKEN=rotated; path=/"))
        }
    }

    @After fun tearDown() = web.close()

    private suspend fun get(url: String) = http.send(Request.Builder().url(url).build())

    @Test
    fun `sends browser headers, cookies and the XSRF token`() = runTest {
        get("https://weibo.com/ajax/config")

        val headers = web.requests.single().headers
        assertEquals(BROWSER_USER_AGENT, headers["User-Agent"])
        assertEquals("https://weibo.com/", headers["Referer"])
        assertEquals("XMLHttpRequest", headers["X-Requested-With"])
        assertEquals("SUB=sub; XSRF-TOKEN=token", headers["Cookie"])
        assertEquals("token", headers["X-XSRF-TOKEN"])
        assertNull(headers["Origin"])
    }

    @Test
    fun `keeps a caller-provided Referer and adds Origin to POSTs`() = runTest {
        http.send(
            Request.Builder()
                .url("https://weibo.com/ajax/statuses/setLike")
                .header("Referer", "https://weibo.com/detail/1")
                .post(FormBody.Builder().add("id", "1").build())
                .build()
        )

        val headers = web.requests.single().headers
        assertEquals("https://weibo.com/detail/1", headers["Referer"])
        assertEquals("https://weibo.com", headers["Origin"])
    }

    @Test
    fun `stores cookies the server sets`() = runTest {
        get("https://weibo.com/ajax/config")

        assertEquals("rotated", jar.value("weibo.com", "XSRF-TOKEN"))
    }

    @Test
    fun `sends no Weibo cookies to other hosts`() = runTest {
        get("https://example.com/")

        val headers = web.requests.single().headers
        assertNull(headers["Cookie"])
        assertNull(headers["X-XSRF-TOKEN"])
    }

    @Test
    fun `a plain cross-origin request has cookies but no XHR headers`() = runTest {
        http.send(
            Request.Builder()
                .url("https://picupload.weibo.com/interface/pic_upload.php")
                .tag(PlainCrossOrigin::class.java, PlainCrossOrigin)
                .post(FormBody.Builder().build())
                .build()
        )

        val headers = web.requests.single().headers
        assertEquals("SUB=sub; XSRF-TOKEN=token", headers["Cookie"])
        assertNull(headers["X-Requested-With"])
        assertNull(headers["X-XSRF-TOKEN"])
        assertEquals("https://weibo.com", headers["Origin"])
    }

    @Test
    fun `follows redirects with each hop's own cookies, keeping what any hop sets`() = runTest {
        web.respond = {
            when (it.host) {
                "weibo.com" -> redirect("https://login.sina.com.cn/sso/login.php")
                "login.sina.com.cn" -> redirect("https://passport.weibo.com/visitor/visitor")
                else ->
                    html("<html>visitor</html>")
                        .newBuilder()
                        .addHeader("Set-Cookie", "SUBP=v; domain=.weibo.com; path=/")
                        .build()
            }
        }

        val reply = get("https://weibo.com/")

        assertEquals("passport.weibo.com", reply.url.host)
        val (first, sina, passport) = web.requests
        assertEquals("SUB=sub; XSRF-TOKEN=token", first.headers["Cookie"])
        assertNull(sina.headers["Cookie"])
        assertNull(sina.headers["X-XSRF-TOKEN"])
        assertEquals("SUB=sub; XSRF-TOKEN=token", passport.headers["Cookie"])
        assertEquals("v", jar.value("weibo.com", "SUBP"))
    }

    @Test
    fun `a failed connection is a NetworkException`() = runTest {
        web.close()
        val failure = runCatching { get("https://weibo.com/") }.exceptionOrNull()
        assertTrue(failure.toString(), failure is NetworkException)
    }
}

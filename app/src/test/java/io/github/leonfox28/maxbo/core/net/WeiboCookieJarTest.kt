package io.github.leonfox28.maxbo.core.net

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeiboCookieJarTest {
    private val web = "https://weibo.com/ajax/config".toHttpUrl()
    private val mobile = "https://m.weibo.cn/api/config".toHttpUrl()

    @Test
    fun `maps hosts to sites`() {
        assertEquals("weibo.com", WeiboCookieJar.siteOf("weibo.com"))
        assertEquals("weibo.com", WeiboCookieJar.siteOf("passport.weibo.com"))
        assertEquals("weibo.cn", WeiboCookieJar.siteOf("m.weibo.cn"))
        assertNull(WeiboCookieJar.siteOf("notweibo.com"))
        assertNull(WeiboCookieJar.siteOf("wx1.sinaimg.cn"))
    }

    @Test
    fun `builds the Cookie header for the request site only`() {
        val jar =
            WeiboCookieJar(
                mapOf(
                    "weibo.com" to mapOf("SUB" to "a", "XSRF-TOKEN" to "x"),
                    "weibo.cn" to mapOf("SUB" to "b"),
                )
            )

        assertEquals("SUB=a; XSRF-TOKEN=x", jar.headerFor(web))
        assertEquals("SUB=b", jar.headerFor(mobile))
        assertNull(jar.headerFor("https://wx1.sinaimg.cn/a.jpg".toHttpUrl()))
    }

    @Test
    fun `applies Set-Cookie headers and notifies only on change`() {
        val jar = WeiboCookieJar(mapOf("weibo.com" to mapOf("XSRF-TOKEN" to "old")))
        var notified = 0
        jar.addListener { notified++ }

        jar.applySetCookies(
            web,
            listOf(
                "XSRF-TOKEN=new; path=/; secure",
                "SUBP=0033Wr=x; expires=Fri, 01-Jan-2100 00:00:00 GMT; domain=.weibo.com",
            ),
        )
        assertEquals("new", jar.value("weibo.com", "XSRF-TOKEN"))
        assertEquals("0033Wr=x", jar.value("weibo.com", "SUBP"))
        assertEquals(1, notified)

        jar.applySetCookies(web, listOf("XSRF-TOKEN=new; path=/"))
        assertEquals(1, notified)
    }

    @Test
    fun `deletes cookies that expire immediately`() {
        val jar =
            WeiboCookieJar(mapOf("weibo.com" to mapOf("SUB" to "a", "SUBP" to "b", "ALF" to "c")))

        jar.applySetCookies(
            web,
            listOf(
                "SUB=deleted; Max-Age=0; path=/",
                "SUBP=deleted; expires=Thu, 01-Jan-1970 00:00:01 GMT",
                // Max-Age wins over Expires.
                "ALF=d; Max-Age=3600; expires=Thu, 01 Jan 1970 00:00:01 GMT",
            ),
        )

        assertEquals(listOf("ALF"), jar.names("weibo.com"))
        assertEquals("d", jar.value("weibo.com", "ALF"))
    }

    @Test
    fun `ignores cookies set for another site and malformed headers`() {
        val jar = WeiboCookieJar()

        jar.applySetCookies(web, listOf("SUB=evil; domain=.weibo.cn", "novalue", "=nameless"))
        jar.applySetCookies("https://example.com/".toHttpUrl(), listOf("SUB=x"))

        assertTrue(jar.isEmpty)
    }

    @Test
    fun `round-trips through a map and drops unknown sites`() {
        val jar =
            WeiboCookieJar(
                mapOf(
                    "weibo.com" to mapOf("SUB" to "a"),
                    "weibo.cn" to emptyMap(),
                    "example.com" to mapOf("x" to "y"),
                )
            )

        assertEquals(mapOf("weibo.com" to mapOf("SUB" to "a")), jar.toMap())
        assertEquals("SUB=a", WeiboCookieJar(jar.toMap()).headerFor(web))
    }

    @Test
    fun `serves OkHttp the cookies of the request's site`() {
        val jar = WeiboCookieJar(mapOf("weibo.com" to mapOf("SUB" to "a")))

        assertEquals(
            listOf("SUB=a"),
            jar.loadForRequest("https://passport.weibo.com/x".toHttpUrl()).map {
                "${it.name}=${it.value}"
            },
        )
        assertTrue(jar.loadForRequest("https://login.sina.com.cn/".toHttpUrl()).isEmpty())
    }
}

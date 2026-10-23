package io.github.leonfox28.maxbo.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WeiboExceptionTest {
    private fun response(
        json: String?,
        status: Int = 200,
        url: String = "https://weibo.com/ajax/config",
    ) = WeiboResponse(status, url.toHttpUrl(), json?.let { Json.parseToJsonElement(it) })

    @Test
    fun `returns the JSON object on success`() {
        val json = unwrapResponse(response("""{"ok":1,"data":{"x":1}}"""))
        assertEquals(JsonPrimitive(1), json["ok"])
        // Some endpoints carry no "ok" field at all.
        assertEquals(setOf("statuses"), unwrapResponse(response("""{"statuses":[]}""")).keys)
    }

    @Test
    fun `ok -100 means signed out, or a captcha when it points at one`() {
        assertThrows(SessionExpiredException::class.java) {
            unwrapResponse(response("""{"ok":-100,"url":"https://weibo.com/login.php"}"""))
        }
        val captcha =
            assertThrows(CaptchaRequiredException::class.java) {
                unwrapResponse(response("""{"ok":-100,"url":"https://m.weibo.cn/captcha?x=1"}"""))
            }
        assertEquals("/captcha", captcha.url.encodedPath)
    }

    @Test
    fun `a redirect to a login host, 401, or 403 asking to log in`() {
        val cases =
            listOf(
                response(null, url = "https://passport.weibo.com/sso/signin"),
                response("""{"ok":0}""", status = 401),
                response("""{"ok":0,"message":"前方有点拥堵，请登录后使用"}""", status = 403),
            )
        for (case in cases) {
            assertThrows(SessionExpiredException::class.java) { unwrapResponse(case) }
        }
    }

    @Test
    fun `rate limit statuses`() {
        for (status in listOf(418, 429, 432)) {
            val e =
                assertThrows(RateLimitedException::class.java) {
                    unwrapResponse(response(null, status = status))
                }
            assertEquals(status, e.statusCode)
        }
    }

    @Test
    fun `API errors keep Weibo's message`() {
        val e =
            assertThrows(WeiboApiException::class.java) {
                unwrapResponse(response("""{"ok":0,"msg":"内容不存在"}"""))
            }
        assertEquals("内容不存在", e.message)
        val server =
            assertThrows(WeiboApiException::class.java) {
                unwrapResponse(response("""{"ok":0}""", status = 500))
            }
        assertEquals(500, server.statusCode)
        assertThrows(WeiboApiException::class.java) { unwrapResponse(response(null)) }
    }

    @Test
    fun `a message of null gives way to msg`() {
        val e =
            assertThrows(WeiboApiException::class.java) {
                unwrapResponse(response("""{"ok":0,"message":null,"msg":"内容不存在"}"""))
            }
        assertEquals("内容不存在", e.message)
    }
}

package io.github.leonfox28.maxbo.core.net

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A failure talking to Weibo, carrying a message fit to show the user. */
sealed class WeiboException(override val message: String) : Exception(message)

/** The stored session is missing, expired, or was rejected. */
class SessionExpiredException : WeiboException("登录已过期，请重新登录")

/** Weibo wants the user to pass a captcha at [url] before continuing. */
class CaptchaRequiredException(val url: HttpUrl) : WeiboException("需要先完成微博的安全验证")

class RateLimitedException(val statusCode: Int) : WeiboException("请求太频繁，请稍后再试")

class NetworkException(cause: Throwable? = null) : WeiboException("网络连接失败，请检查网络") {
    init {
        cause?.let { initCause(it) }
    }
}

/** Weibo answered, but with an error or a response we don't understand. */
class WeiboApiException(message: String, val statusCode: Int? = null) : WeiboException(message)

private val rateLimitStatuses = setOf(418, 429, 432)

fun isRateLimitStatus(statusCode: Int): Boolean = statusCode in rateLimitStatuses

private val loginHosts = setOf("passport.weibo.com", "passport.weibo.cn", "login.sina.com.cn")

fun isLoginHost(host: String): Boolean = host in loginHosts

/**
 * What came back for a request: the [status], the [url] it ended up at after redirects, and the
 * [json] body if it was JSON.
 */
class WeiboResponse(val status: Int, val url: HttpUrl, val json: JsonElement?)

/**
 * Returns the JSON object of a Weibo web API [response], or throws the matching [WeiboException].
 *
 * The client accepts every HTTP status, so all of Weibo's ways of saying "no" are interpreted here
 * in one place.
 */
fun unwrapResponse(response: WeiboResponse): JsonObject {
    val status = response.status
    val data = response.json

    if (isLoginHost(response.url.host)) throw SessionExpiredException()
    if (isRateLimitStatus(status)) throw RateLimitedException(status)
    if (status == 401) throw SessionExpiredException()

    if (data is JsonObject) {
        // Numbers and booleans only, as Weibo sends them; JSON null is absent.
        val ok = (data["ok"] as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }
        val message =
            (data["message"]?.takeUnless { it is JsonNull } ?: data["msg"]) as? JsonPrimitive
        val messageText = message?.takeUnless { it is JsonNull }?.content.orEmpty()
        if (ok?.longOrNull == -100L) {
            val url = (data["url"] as? JsonPrimitive)?.content?.toHttpUrlOrNull()
            if (url != null && url.toString().contains("captcha")) {
                throw CaptchaRequiredException(url)
            }
            throw SessionExpiredException()
        }
        // e.g. 403 {"ok":0,"message":"前方有点拥堵，请登录后使用"}
        if (status == 403 && messageText.contains("登录")) throw SessionExpiredException()
        val succeeded = ok == null || ok.longOrNull == 1L || ok.booleanOrNull == true
        if (status >= 400 || !succeeded) {
            throw WeiboApiException(
                if (message?.isString == true && messageText.isNotEmpty()) messageText
                else "请求失败（$status）",
                statusCode = status,
            )
        }
        return data
    }

    if (status >= 400) throw WeiboApiException("请求失败（$status）", statusCode = status)
    throw WeiboApiException("微博返回了无法识别的内容", statusCode = status)
}

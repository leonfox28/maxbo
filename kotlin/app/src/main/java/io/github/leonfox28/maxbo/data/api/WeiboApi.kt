package io.github.leonfox28.maxbo.data.api

import io.github.leonfox28.maxbo.core.net.HttpReply
import io.github.leonfox28.maxbo.core.net.RateLimitedException
import io.github.leonfox28.maxbo.core.net.SessionExpiredException
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.core.net.WeiboHttp
import io.github.leonfox28.maxbo.core.net.isLoginHost
import io.github.leonfox28.maxbo.core.net.isRateLimitStatus
import io.github.leonfox28.maxbo.core.net.unwrapResponse
import io.github.leonfox28.maxbo.core.util.describeShape
import io.github.leonfox28.maxbo.core.util.devLog
import io.github.leonfox28.maxbo.data.model.Account
import io.github.leonfox28.maxbo.data.model.FeedPage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Weibo's web endpoints, as used by weibo.com in a browser.
 *
 * @param onSessionExpired called whenever Weibo rejects the session of a request sent at the given
 *   time (on [clock]); returns whether the session has been renewed.
 */
class WeiboApi(
    private val http: WeiboHttp,
    private val onSessionExpired: (suspend (sentAt: Long) -> Boolean)? = null,
    private val clock: () -> Long = System::nanoTime,
) {
    /**
     * The signed-in account; throws [SessionExpiredException] for visitors.
     *
     * weibo.com has no JSON endpoint for this; the web app reads the user from the `$CONFIG` object
     * embedded in its home page, and so do we.
     */
    suspend fun fetchCurrentAccount(): Account {
        val html = getPage("$WEB/")
        val config = extractPageConfig(html)
        if (config == null) {
            devLog("weibo.com page has no \$CONFIG (${html.length} chars)")
            throw WeiboApiException("无法识别当前登录的账号")
        }
        return Account.tryFromPageConfig(config)
            ?: run {
                devLog("no user in \$CONFIG: ${describeShape(config, maxDepth = 1)}")
                throw SessionExpiredException()
            }
    }

    /**
     * A page of the "最新微博" timeline of [uid]: the newest page, or the one after [maxId] (a previous
     * page's [FeedPage.nextCursor]).
     */
    suspend fun fetchHomeTimeline(uid: String, maxId: String? = null): FeedPage {
        val json =
            getJson(
                "$WEB/ajax/feed/friendstimeline",
                mapOf(
                    // The built-in "最新微博" group; see PLAN §8 item 3.
                    "list_id" to "11000$uid",
                    "refresh" to 4,
                    "count" to 20,
                    if (maxId == null) "since_id" to 0 else "max_id" to maxId,
                ),
            )
        val page = FeedPage.fromJson(json)
        devLog("timeline: ${page.statuses.size} statuses, more=${page.nextCursor != null}")
        return page
    }

    /** Reads a JSON endpoint; [query] values that are null are left out. */
    suspend fun getJson(url: String, query: Map<String, Any?> = emptyMap()): JsonObject =
        guard(resend = true) {
            val request = Request.Builder().url(withQuery(url, query)).build()
            val reply = http.send(request)
            val response = reply.toWeiboResponse()
            val json = response.json
            log(
                request,
                reply,
                "ok=${if (json is JsonObject) json["ok"] else json?.let { "json" } ?: "text"}",
            )
            unwrapResponse(response)
        }

    /**
     * Posts [body] as a form, as weibo.com does for everything that changes something. Fields that
     * are null aren't sent.
     */
    suspend fun postForm(url: String, body: Map<String, Any?>): JsonObject = guard {
        val form = FormBody.Builder()
        for ((name, value) in body) if (value != null) form.add(name, value.toString())
        val request = Request.Builder().url(url).post(form.build()).build()
        val reply = http.send(request)
        val response = reply.toWeiboResponse()
        val data = response.json
        log(
            request,
            reply,
            if (data is JsonObject) "ok=${data["ok"]} ${describeShape(data, maxDepth = 0)}"
            else "not an object",
        )
        val json = unwrapResponse(response)
        // An answer can say ok and still carry an error.
        val error = (json["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (!error.isNullOrEmpty()) throw WeiboApiException(error, statusCode = reply.status)
        json
    }

    /** Fetches an HTML page as a signed-in browser would. */
    suspend fun getPage(url: String): String =
        guard(resend = true) {
            val request =
                Request.Builder()
                    .url(url)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()
            val reply = http.send(request)
            log(request, reply, "${reply.body.length} chars")
            if (isLoginHost(reply.url.host)) throw SessionExpiredException()
            if (isRateLimitStatus(reply.status)) throw RateLimitedException(reply.status)
            if (reply.status != 200) {
                throw WeiboApiException("请求失败（${reply.status}）", statusCode = reply.status)
            }
            reply.body
        }

    /**
     * Runs [request]. When Weibo rejects the session and it gets renewed, a request that only reads
     * is sent again, once; others fail asking the user to try again, as sending a change twice is
     * theirs to decide.
     */
    private suspend fun <T> guard(resend: Boolean = false, request: suspend () -> T): T {
        val sentAt = clock()
        try {
            return request()
        } catch (e: SessionExpiredException) {
            val renewed = onSessionExpired?.invoke(sentAt) ?: false
            if (!renewed) throw e
            if (resend) return guard(request = request)
            throw WeiboApiException("登录状态已更新，请再试一次")
        }
    }

    private fun withQuery(url: String, query: Map<String, Any?>) =
        url.toHttpUrl()
            .newBuilder()
            .apply {
                for ((name, value) in query) {
                    if (value != null) addQueryParameter(name, value.toString())
                }
            }
            .build()

    private fun log(request: Request, reply: HttpReply, detail: String) {
        devLog(
            "${request.method} ${request.url.encodedPath} -> ${reply.status} $detail " +
                "final=${reply.url.host}${reply.url.encodedPath}"
        )
    }

    companion object {
        const val WEB = "https://weibo.com"
    }
}

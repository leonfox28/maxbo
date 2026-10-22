package io.github.leonfox28.maxbo.core.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The browser identity used by both the login WebView and API requests, so Weibo sees one
 * consistent desktop browser throughout the session.
 */
const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

/**
 * Tags a request to send it as weibo.com's pages send a plain cross-origin request, such as a
 * picture upload: with cookies, but without the XHR and XSRF headers of its own endpoints.
 */
object PlainCrossOrigin

/** What came back for a request: its status, the URL it ended up at, and its body. */
class HttpReply(val status: Int, val url: HttpUrl, val contentType: String?, val body: String) {
    /** The body as JSON, if it was sent as JSON and parses. */
    fun json(): JsonElement? {
        val type = contentType?.lowercase() ?: return null
        if (
            !type.contains("application/json") &&
                !type.contains("text/json") &&
                !type.contains("+json")
        ) {
            return null
        }
        return runCatching { Json.parseToJsonElement(body) }.getOrNull()
    }

    fun toWeiboResponse() = WeiboResponse(status, url, json())
}

/**
 * Calls Weibo's web endpoints the way a browser tab on weibo.com would, with the cookies in a
 * [WeiboCookieJar], spaced out by a [RequestThrottle].
 *
 * Every HTTP status is accepted; use [unwrapResponse] to interpret replies. Requests are never sent
 * again on their own (PLAN §2), except that OkHttp may reconnect a read whose pooled connection
 * turns out to be dead, as a browser does; writes don't even do that, so a change can't be sent
 * twice.
 */
class WeiboHttp
private constructor(
    private val reads: OkHttpClient,
    private val writes: OkHttpClient,
    private val throttle: RequestThrottle,
) {
    /**
     * Sends [request] once the throttle lets it, and reads the whole reply.
     *
     * @throws NetworkException if the request couldn't be sent or the reply not read.
     */
    suspend fun send(request: Request): HttpReply {
        val client = if (request.method == "GET") reads else writes
        throttle.acquire()
        try {
            val response =
                try {
                    client.newCall(request).await()
                } catch (e: IOException) {
                    throw NetworkException(e)
                }
            response.use {
                if (isRateLimitStatus(it.code)) throttle.backOff() else throttle.resetBackOff()
                val body =
                    try {
                        withContext(Dispatchers.IO) { it.body.string() }
                    } catch (e: IOException) {
                        throw NetworkException(e)
                    }
                return HttpReply(it.code, it.request.url, it.header("Content-Type"), body)
            }
        } finally {
            throttle.release()
        }
    }

    companion object {
        /**
         * A client on [base]'s connections and threads, with the cookies in [jar].
         *
         * @param base tests swap in one that reaches a fake Weibo.
         */
        fun create(
            base: OkHttpClient,
            jar: WeiboCookieJar,
            throttle: RequestThrottle,
            userAgent: String = BROWSER_USER_AGENT,
        ): WeiboHttp {
            val reads =
                base
                    .newBuilder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .cookieJar(jar)
                    .addInterceptor(BrowserHeaders(userAgent))
                    .addNetworkInterceptor(WeiboHeaders(jar))
                    .build()
            val writes = reads.newBuilder().retryOnConnectionFailure(false).build()
            return WeiboHttp(reads, writes, throttle)
        }
    }
}

/** Headers every request has, as a browser sends them. */
private class BrowserHeaders(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().header("User-Agent", userAgent)
        if (request.header("Accept") == null) {
            builder.header("Accept", "application/json, text/plain, */*")
        }
        if (request.header("Accept-Language") == null) {
            builder.header("Accept-Language", "zh-CN,zh;q=0.9")
        }
        return chain.proceed(builder.build())
    }
}

/**
 * Adds the headers Weibo's XHR endpoints expect. A network interceptor, so it runs for every hop,
 * and a redirect to another site gets that site's headers, or none.
 */
private class WeiboHeaders(private val jar: WeiboCookieJar) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val site = WeiboCookieJar.siteOf(request.url.host) ?: return chain.proceed(request)
        val origin =
            if (site == WeiboCookieJar.MOBILE) "https://m.weibo.cn" else "https://weibo.com"
        val xhr = request.tag(PlainCrossOrigin::class.java) == null
        val builder = request.newBuilder()
        if (request.header("Referer") == null) builder.header("Referer", "$origin/")
        if (xhr) {
            builder.header("X-Requested-With", "XMLHttpRequest")
            jar.value(site, "XSRF-TOKEN")?.let { builder.header("X-XSRF-TOKEN", it) }
        }
        if (request.method != "GET") builder.header("Origin", origin)
        return chain.proceed(builder.build())
    }
}

/** Runs the call on OkHttp's threads; cancelling the coroutine cancels it. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, _, _ -> response.close() }
            }
        }
    )
}

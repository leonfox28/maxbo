package io.github.leonfox28.maxbo.core.session

import android.webkit.CookieManager
import io.github.leonfox28.maxbo.core.net.WeiboCookieJar
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The WebView's cookie store, shared by the login page, the in-app browser and session renewal.
 * Weibo's session cookie (`SUB`) is HttpOnly: page scripts can't see it, but the store can.
 */
interface WebCookies {
    /** The cookies the WebView would send to [url], keyed by name. */
    suspend fun cookies(url: String): Map<String, String>

    /** The WebView's cookies for Weibo's two sites, keyed by site. */
    suspend fun readWeiboCookies(): Map<String, Map<String, String>> =
        mapOf(
            WeiboCookieJar.WEB to cookies("https://weibo.com/"),
            WeiboCookieJar.MOBILE to cookies("https://m.weibo.cn/"),
        )

    /** Deletes every WebView cookie, signing the embedded browser out. */
    suspend fun clearAll()
}

class WebViewCookies : WebCookies {
    override suspend fun cookies(url: String): Map<String, String> =
        withContext(Dispatchers.Main) {
            parseCookieHeader(CookieManager.getInstance().getCookie(url))
        }

    override suspend fun clearAll() =
        withContext(Dispatchers.Main) {
            val manager = CookieManager.getInstance()
            suspendCancellableCoroutine { continuation ->
                manager.removeAllCookies { continuation.resume(Unit) }
            }
            manager.flush()
        }
}

/**
 * The cookies in a `Cookie` header such as CookieManager gives, `a=1; b=2`. Values may contain `=`.
 * When names repeat (one cookie per domain, say), the last one wins.
 */
fun parseCookieHeader(header: String?): Map<String, String> = buildMap {
    for (part in header.orEmpty().split(';')) {
        val pair = part.trim()
        val eq = pair.indexOf('=')
        if (eq > 0) put(pair.substring(0, eq), pair.substring(eq + 1))
    }
}

package io.github.leonfox28.maxbo.core.session

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import io.github.leonfox28.maxbo.core.net.BROWSER_USER_AGENT
import io.github.leonfox28.maxbo.core.net.WeiboCookieJar
import io.github.leonfox28.maxbo.core.util.devLog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

typealias Cookies = Map<String, Map<String, String>>

/**
 * Renews a lapsed session the way a browser does.
 *
 * Weibo honours `SUB` for about a week, but signing in also leaves a 30-day auto-login cookie
 * (`ALC`) on passport.weibo.com. A browser that opens weibo.com with a lapsed `SUB` ends up on
 * passport's visitor page (at times after a detour through login.sina.com.cn), whose script trades
 * `ALC` for a new `SUB` and another 30 days (PLAN §8). Renewing lets Weibo's own pages do just that
 * in a WebView, which shares its cookies with the login page.
 */
interface SessionRenewer {
    /**
     * Opens weibo.com and offers the WebView's cookies to [adopt] each time a page leaves a `SUB`
     * other than [lapsedSub] and those offered before.
     *
     * Returns true once [adopt] accepts them, or false when it turns down one Weibo set meanwhile
     * (a visitor's, when there was nothing to renew), or when Weibo takes too long.
     */
    suspend fun renew(lapsedSub: String?, adopt: suspend (Cookies) -> Boolean): Boolean
}

/** Follows the pages of one renewal; see [SessionRenewer.renew]. */
class RenewalTracker(
    lapsedSub: String?,
    /** The WebView's `SUB` before the first page. */
    private val startSub: String?,
    private val adopt: suspend (Cookies) -> Boolean,
) {
    private val offered = mutableSetOf(lapsedSub)

    /**
     * Handles the [cookies] a main-frame page left behind: true once [adopt] accepted them, false
     * if Weibo won't renew, or null to wait for the next page.
     */
    suspend fun pageLoaded(cookies: Cookies): Boolean? {
        val sub = cookies[WeiboCookieJar.WEB]?.get("SUB")
        if (sub == null || !offered.add(sub)) return null
        if (adopt(cookies)) return true
        // The WebView's own session may have lapsed too, and is renewed in
        // turn; one Weibo set on the way is all it is going to set.
        return if (sub == startSub) null else false
    }
}

/**
 * Renews in a WebView that is never shown, made from the application's [context] so it outlives any
 * page.
 */
class WebViewSessionRenewer(
    private val context: Context,
    private val webCookies: WebCookies,
    private val timeout: Duration = 20.seconds,
) : SessionRenewer {
    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun renew(lapsedSub: String?, adopt: suspend (Cookies) -> Boolean): Boolean =
        withContext(Dispatchers.Main) {
            val tracker =
                RenewalTracker(
                    lapsedSub = lapsedSub,
                    startSub = webCookies.readWeiboCookies()[WeiboCookieJar.WEB]?.get("SUB"),
                    adopt = adopt,
                )
            val result = CompletableDeferred<Boolean>()
            // Pages are handled one at a time: adopting one takes a request.
            val pages = Channel<Pair<String?, String?>>(Channel.UNLIMITED)
            val webView = WebView(context)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = BROWSER_USER_AGENT
            }
            webView.webViewClient =
                object : WebViewClient() {
                    // Only web pages; once done, only the blank page that ends them.
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url
                        devLog("session renewal: to ${url.host}${url.path}")
                        val allowed =
                            if (result.isCompleted) url.toString() == BLANK
                            else url.scheme == "https" || url.scheme == "http"
                        return !allowed
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        pages.trySend(url to null)
                    }

                    // passport's last hop, through login.sina.com.cn, fails in a
                    // WebView after the weibo.com cookies are set (PLAN §8 item 1).
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) {
                            pages.trySend(request.url.toString() to "failed (${error.errorCode})")
                        }
                    }
                }
            try {
                coroutineScope {
                    val handler = launch {
                        for ((url, error) in pages) {
                            if (result.isCompleted) break
                            // No query: passport's carries tickets.
                            val page = url?.toUri()
                            try {
                                val cookies = webCookies.readWeiboCookies()
                                devLog(
                                    "session renewal: ${page?.host}${page?.path} ${error ?: "loaded"}, " +
                                        "weibo.com=${cookies[WeiboCookieJar.WEB]?.keys?.toList()}"
                                )
                                tracker.pageLoaded(cookies)?.let { result.complete(it) }
                            } catch (e: Exception) {
                                devLog("session renewal: ${e.javaClass.simpleName}")
                            }
                        }
                    }
                    webView.loadUrl("https://weibo.com/")
                    val renewed = withTimeoutOrNull(timeout) { result.await() } ?: false
                    result.complete(renewed)
                    handler.cancel()
                    renewed
                }
            } finally {
                // Leave nothing running: weibo.com's own pages poll for news.
                webView.stopLoading()
                webView.loadUrl(BLANK)
                webView.destroy()
            }
        }

    private companion object {
        const val BLANK = "about:blank"
    }
}

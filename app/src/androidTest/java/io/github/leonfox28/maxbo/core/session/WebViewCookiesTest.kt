package io.github.leonfox28.maxbo.core.session

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that [WebViewCookies] reads the store the WebView uses, including HttpOnly cookies set by
 * a page (Weibo's SUB is HttpOnly and invisible to page scripts).
 *
 * Clears every WebView cookie of the app under test: run it only on an emulator without a real
 * login (KOTLIN_MIGRATION.md §7).
 */
@RunWith(AndroidJUnit4::class)
class WebViewCookiesTest {
    private val cookies = WebViewCookies()

    @Before fun setUp() = runBlocking { cookies.clearAll() }

    @After fun tearDown() = runBlocking { cookies.clearAll() }

    @Test
    fun readsCookiesWrittenToTheStore() = runBlocking {
        withContext(Dispatchers.Main) {
            CookieManager.getInstance().setCookie("https://example.com/", "maxbo_probe=probe_1")
        }

        assertEquals("probe_1", cookies.cookies("https://example.com/")["maxbo_probe"])
    }

    @Test
    fun readsHttpOnlyCookiesSetByAPageResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Set-Cookie", "maxbo_http_only=probe_2; HttpOnly; Path=/")
                    .addHeader("Content-Type", "text/html")
                    .body("<html><body>ok</body></html>")
                    .build()
            )
            server.start()
            // The network security config lets debug builds reach localhost
            // over plain HTTP, and nothing else.
            val url = server.url("/").newBuilder().host("localhost").build().toString()

            val loaded = CompletableDeferred<Unit>()
            val webView =
                withContext(Dispatchers.Main) {
                    WebView(ApplicationProvider.getApplicationContext()).apply {
                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    loaded.complete(Unit)
                                }
                            }
                        loadUrl(url)
                    }
                }
            try {
                withTimeout(20.seconds) { loaded.await() }
                assertEquals("probe_2", cookies.cookies(url)["maxbo_http_only"])
            } finally {
                withContext(Dispatchers.Main) { webView.destroy() }
            }
        }
    }

    @Test
    fun clearsEveryCookie() = runBlocking {
        withContext(Dispatchers.Main) {
            CookieManager.getInstance().setCookie("https://weibo.com/", "SUB=x; domain=.weibo.com")
        }

        cookies.clearAll()

        assertTrue(cookies.readWeiboCookies().values.all { it.isEmpty() })
    }
}

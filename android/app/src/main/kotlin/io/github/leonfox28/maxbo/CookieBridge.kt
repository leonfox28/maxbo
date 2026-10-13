package io.github.leonfox28.maxbo

import android.webkit.CookieManager
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/**
 * Reads cookies from the system WebView cookie store, including HttpOnly ones
 * (such as Weibo's SUB) that page scripts and webview_flutter cannot read.
 */
object CookieBridge {
    private const val CHANNEL = "io.github.leonfox28.maxbo/cookies"

    fun register(messenger: BinaryMessenger) {
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method != "getCookies") {
                result.notImplemented()
                return@setMethodCallHandler
            }
            val url = call.argument<String>("url")
            if (url == null) {
                result.error("bad_args", "url is required", null)
                return@setMethodCallHandler
            }
            result.success(parse(CookieManager.getInstance().getCookie(url)))
        }
    }

    // CookieManager returns a header like "a=1; b=2"; values may contain '='.
    private fun parse(header: String?): List<Map<String, String>> =
        header.orEmpty().split(';').mapNotNull { part ->
            val pair = part.trim()
            val eq = pair.indexOf('=')
            if (eq <= 0) null else mapOf("name" to pair.substring(0, eq), "value" to pair.substring(eq + 1))
        }
}

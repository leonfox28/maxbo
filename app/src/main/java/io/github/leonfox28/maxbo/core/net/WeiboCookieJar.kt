package io.github.leonfox28.maxbo.core.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Cookies for Weibo's two sites, kept in memory and scoped per site.
 *
 * The web API spans weibo.com (desktop web API, passport) and weibo.cn (m.weibo.cn). Scoping per
 * site instead of per exact domain and path is all these endpoints need, and keeps the jar trivial
 * to persist.
 *
 * As OkHttp's [CookieJar] it serves every hop of a request, redirects included, so cookies never
 * reach a host outside their site, and those any hop sets are kept.
 */
class WeiboCookieJar(cookies: Map<String, Map<String, String>> = emptyMap()) : CookieJar {
    private val sites = mutableMapOf<String, LinkedHashMap<String, String>>()
    private val listeners = mutableListOf<() -> Unit>()

    init {
        replace(cookies)
    }

    val isEmpty: Boolean
        get() = synchronized(this) { sites.values.all { it.isEmpty() } }

    fun value(site: String, name: String): String? = synchronized(this) { sites[site]?.get(name) }

    fun names(site: String): List<String> =
        synchronized(this) { sites[site]?.keys?.toList().orEmpty() }

    /** The `Cookie` header for a request to [url], or null if there is none. */
    fun headerFor(url: HttpUrl): String? =
        synchronized(this) {
            val cookies = siteOf(url.host)?.let { sites[it] }
            if (cookies.isNullOrEmpty()) null
            else cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

    /** Applies the `Set-Cookie` [headers] of a response from [url]. */
    fun applySetCookies(url: HttpUrl, headers: List<String>) {
        apply(url, headers.mapNotNull { Cookie.parse(url, it) })
    }

    fun replaceAll(cookies: Map<String, Map<String, String>>) {
        synchronized(this) { replace(cookies) }
        notifyListeners()
    }

    fun clear() {
        val changed = synchronized(this) { sites.isNotEmpty().also { sites.clear() } }
        if (changed) notifyListeners()
    }

    fun toMap(): Map<String, Map<String, String>> =
        synchronized(this) {
            sites.filterValues { it.isNotEmpty() }.mapValues { LinkedHashMap(it.value) }
        }

    /** Calls [listener] after every change, on the thread that made it. */
    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners += listener }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners -= listener }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        synchronized(this) {
            val site = siteOf(url.host) ?: return emptyList()
            sites[site].orEmpty().mapNotNull { (name, value) ->
                runCatching { Cookie.Builder().name(name).value(value).domain(site).build() }
                    .getOrNull()
            }
        }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        apply(url, cookies)
    }

    // OkHttp already drops cookies whose domain the response's host can't set,
    // as a browser does.
    private fun apply(url: HttpUrl, cookies: List<Cookie>) {
        val site = siteOf(url.host) ?: return
        val now = System.currentTimeMillis()
        val changed =
            synchronized(this) {
                val jar = sites.getOrPut(site) { LinkedHashMap() }
                var changed = false
                for (cookie in cookies) {
                    if (siteOf(cookie.domain) != site) continue
                    if (cookie.expiresAt <= now) {
                        changed = jar.remove(cookie.name) != null || changed
                    } else if (jar[cookie.name] != cookie.value) {
                        jar[cookie.name] = cookie.value
                        changed = true
                    }
                }
                changed
            }
        if (changed) notifyListeners()
    }

    private fun replace(cookies: Map<String, Map<String, String>>) {
        sites.clear()
        for ((site, values) in cookies) {
            if (site in SITES) sites[site] = LinkedHashMap(values)
        }
    }

    private fun notifyListeners() {
        for (listener in synchronized(listeners) { listeners.toList() }) listener()
    }

    companion object {
        const val WEB = "weibo.com"
        const val MOBILE = "weibo.cn"
        val SITES = listOf(WEB, MOBILE)

        /** The site [host] belongs to, or null if it isn't a Weibo host. */
        fun siteOf(host: String): String? = SITES.firstOrNull {
            host == it || host.endsWith(".$it")
        }
    }
}

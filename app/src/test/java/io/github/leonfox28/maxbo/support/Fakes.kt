package io.github.leonfox28.maxbo.support

import io.github.leonfox28.maxbo.core.media.PictureCache
import io.github.leonfox28.maxbo.core.net.WeiboCookieJar
import io.github.leonfox28.maxbo.core.session.SessionRenewer
import io.github.leonfox28.maxbo.core.session.SessionStorage
import io.github.leonfox28.maxbo.core.session.StoredSession
import io.github.leonfox28.maxbo.core.session.WebCookies
import io.github.leonfox28.maxbo.core.settings.AppSettings
import io.github.leonfox28.maxbo.core.settings.SettingsStorage

/** Keeps settings in memory, counting the saves. */
class InMemorySettingsStorage(var stored: AppSettings = AppSettings()) : SettingsStorage {
    var saves = 0
        private set

    override suspend fun load() = stored

    override suspend fun save(settings: AppSettings) {
        saves++
        stored = settings
    }
}

class InMemorySessionStorage(var stored: StoredSession? = null) : SessionStorage {
    var saves = 0
        private set

    override suspend fun load() = stored

    override suspend fun save(session: StoredSession) {
        saves++
        stored = session
    }

    override suspend fun clear() {
        stored = null
    }
}

class FakeWebCookies(var cookies: Map<String, Map<String, String>> = emptyMap()) : WebCookies {
    var cleared = false
        private set

    override suspend fun cookies(url: String): Map<String, String> =
        cookies[if (url.contains("weibo.cn")) WeiboCookieJar.MOBILE else WeiboCookieJar.WEB]
            .orEmpty()

    override suspend fun readWeiboCookies() = cookies

    override suspend fun clearAll() {
        cleared = true
        cookies = emptyMap()
    }
}

/** A renewal whose pages leave [cookies], or nothing new when they're null. */
class FakeSessionRenewer(var cookies: Map<String, Map<String, String>>? = null) : SessionRenewer {
    var runs = 0
        private set

    override suspend fun renew(
        lapsedSub: String?,
        adopt: suspend (Map<String, Map<String, String>>) -> Boolean,
    ): Boolean {
        runs++
        val cookies = cookies
        if (cookies == null || cookies[WeiboCookieJar.WEB]?.get("SUB") == lapsedSub) return false
        return adopt(cookies)
    }
}

/** Pictures kept on disk, [size] bytes of them, recording what the app asks of them. */
class FakePictureCache(@Volatile var size: Long = 0) : PictureCache {
    val limits = mutableListOf<Long>()
    var clears = 0

    override suspend fun sizeBytes() = size

    override suspend fun clear() {
        clears++
        size = 0
    }

    override suspend fun setLimit(maxBytes: Long) {
        limits += maxBytes
        size = minOf(size, maxBytes)
    }
}

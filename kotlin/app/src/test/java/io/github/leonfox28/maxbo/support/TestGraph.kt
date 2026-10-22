package io.github.leonfox28.maxbo.support

import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.core.net.RequestThrottle
import io.github.leonfox28.maxbo.core.net.WeiboCookieJar
import io.github.leonfox28.maxbo.core.net.WeiboHttp
import io.github.leonfox28.maxbo.core.session.SessionController
import io.github.leonfox28.maxbo.core.session.StoredSession
import io.github.leonfox28.maxbo.core.settings.AppSettings
import io.github.leonfox28.maxbo.core.settings.SettingsController
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Signed in as uid 7, as `signedInOverrides()` was in the Flutter tests. */
val signedIn = StoredSession(Account(uid = "7"), mapOf(WeiboCookieJar.WEB to mapOf("SUB" to "x")))

/**
 * An [AppGraph] of fakes, as `signedInOverrides()` gave the Flutter tests: signed out unless
 * [restored] says otherwise, with requests going to [web] (or nowhere, without one).
 */
fun testGraph(
    settings: AppSettings = AppSettings(),
    settingsStorage: InMemorySettingsStorage = InMemorySettingsStorage(settings),
    restored: StoredSession? = null,
    sessionStorage: InMemorySessionStorage = InMemorySessionStorage(restored),
    webCookies: FakeWebCookies = FakeWebCookies(),
    renewer: FakeSessionRenewer = FakeSessionRenewer(),
    web: FakeWeibo? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
): AppGraph {
    val base = web?.client ?: OkHttpClient()
    val throttle = RequestThrottle(minIntervalMs = 0)
    val httpFor = { jar: WeiboCookieJar -> WeiboHttp.create(base, jar, throttle) }
    val session = SessionController(restored, sessionStorage, webCookies, renewer, httpFor, scope)
    return AppGraph(
        appScope = scope,
        settings = SettingsController(settings, settingsStorage, scope),
        session = session,
        api = WeiboApi(httpFor(session.jar), onSessionExpired = session::renewOrExpire),
        webCookies = webCookies,
    )
}

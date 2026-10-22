package io.github.leonfox28.maxbo.support

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.test.FakeImageLoaderEngine
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
import io.github.leonfox28.maxbo.ui.timeline.TimelineController
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/** Signed in as uid 7, as `signedInOverrides()` was in the Flutter tests. */
val signedIn = StoredSession(Account(uid = "7"), mapOf(WeiboCookieJar.WEB to mapOf("SUB" to "x")))

/**
 * An [AppGraph] of fakes, as `signedInOverrides()` gave the Flutter tests: signed out unless
 * [restored] says otherwise, with requests going to [web], or failing as if offline without one.
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
    val base = web?.client ?: offlineClient
    val throttle = RequestThrottle(minIntervalMs = 0)
    val httpFor = { jar: WeiboCookieJar -> WeiboHttp.create(base, jar, throttle) }
    val session = SessionController(restored, sessionStorage, webCookies, renewer, httpFor, scope)
    val api = WeiboApi(httpFor(session.jar), onSessionExpired = session::renewOrExpire)
    return AppGraph(
        appScope = scope,
        settings = SettingsController(settings, settingsStorage, scope),
        session = session,
        api = api,
        webCookies = webCookies,
        timeline = TimelineController(api, session, scope),
        imageLoader = { fakeImageLoader() },
    )
}

/** A client that never reaches the network: every request fails as if offline. */
private val offlineClient =
    OkHttpClient.Builder()
        .addInterceptor(Interceptor { throw IOException("no network in tests") })
        .build()

/** An image loader that never reaches the network: every picture loads as a grey square. */
@OptIn(ExperimentalCoilApi::class)
fun fakeImageLoader(): ImageLoader =
    ImageLoader.Builder(ApplicationProvider.getApplicationContext<Context>())
        .components {
            add(FakeImageLoaderEngine.Builder().default(ColorImage(0xFF808080.toInt())).build())
        }
        .build()

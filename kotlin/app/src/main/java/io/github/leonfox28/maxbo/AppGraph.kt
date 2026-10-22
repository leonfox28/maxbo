package io.github.leonfox28.maxbo

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import coil3.ImageLoader
import io.github.leonfox28.maxbo.core.media.weiboImageLoader
import io.github.leonfox28.maxbo.core.net.RequestThrottle
import io.github.leonfox28.maxbo.core.net.WeiboCookieJar
import io.github.leonfox28.maxbo.core.net.WeiboHttp
import io.github.leonfox28.maxbo.core.session.EncryptedFileSessionStorage
import io.github.leonfox28.maxbo.core.session.KeystoreSessionCipher
import io.github.leonfox28.maxbo.core.session.SessionController
import io.github.leonfox28.maxbo.core.session.WebCookies
import io.github.leonfox28.maxbo.core.session.WebViewCookies
import io.github.leonfox28.maxbo.core.session.WebViewSessionRenewer
import io.github.leonfox28.maxbo.core.settings.DataStoreSettingsStorage
import io.github.leonfox28.maxbo.core.settings.SettingsController
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.ui.timeline.TimelineController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/**
 * The app's long-lived objects, made by hand: what Riverpod's providers were in the Flutter app.
 * Tests build one from fakes.
 */
class AppGraph(
    /** For work that outlives any page, such as saving a setting. */
    val appScope: CoroutineScope,
    val settings: SettingsController,
    val session: SessionController,
    /** Signed in as [session] says, renewing the session when Weibo rejects it. */
    val api: WeiboApi,
    val webCookies: WebCookies,
    val timeline: TimelineController,
    imageLoader: () -> ImageLoader,
) {
    /** Loads Weibo's pictures; made on first use. */
    val imageLoader: ImageLoader by lazy(imageLoader)

    companion object {
        /** The real thing, reading the saved settings and session before the first frame. */
        fun create(context: Context): AppGraph {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val settingsStorage =
                DataStoreSettingsStorage(
                    PreferenceDataStoreFactory.create {
                        context.preferencesDataStoreFile("settings")
                    }
                )
            // Never backed up, whatever the backup rules say.
            val sessionStorage =
                EncryptedFileSessionStorage(
                    File(context.noBackupFilesDir, "session"),
                    KeystoreSessionCipher(),
                )
            // Before the first frame, so a chosen theme doesn't flash the
            // default, and a signed-in user doesn't see the login prompt.
            val (initialSettings, restored) =
                runBlocking(Dispatchers.IO) { settingsStorage.load() to sessionStorage.load() }

            val throttle = RequestThrottle()
            val base = OkHttpClient()
            val httpFor = { jar: WeiboCookieJar -> WeiboHttp.create(base, jar, throttle) }
            val webCookies = WebViewCookies()
            val session =
                SessionController(
                    restored = restored,
                    storage = sessionStorage,
                    webCookies = webCookies,
                    renewer = WebViewSessionRenewer(context.applicationContext, webCookies),
                    httpFor = httpFor,
                    scope = scope,
                )
            val api = WeiboApi(httpFor(session.jar), onSessionExpired = session::renewOrExpire)
            return AppGraph(
                appScope = scope,
                settings = SettingsController(initialSettings, settingsStorage, scope),
                session = session,
                api = api,
                webCookies = webCookies,
                timeline = TimelineController(api, session, scope),
                imageLoader = {
                    weiboImageLoader(
                        context.applicationContext,
                        base,
                        initialSettings.imageCacheLimitMb * 1024L * 1024,
                    )
                },
            )
        }
    }
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("No AppGraph provided") }

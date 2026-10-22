package io.github.leonfox28.maxbo

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.leonfox28.maxbo.core.settings.DataStoreSettingsStorage
import io.github.leonfox28.maxbo.core.settings.SettingsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * The app's long-lived objects, made by hand: what Riverpod's providers were in the Flutter app.
 * Tests build one from fakes.
 */
class AppGraph(
    /** For work that outlives any page, such as saving a setting. */
    val appScope: CoroutineScope,
    val settings: SettingsController,
) {
    companion object {
        /** The real thing, reading the saved settings before the first frame. */
        fun create(context: Context): AppGraph {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val storage =
                DataStoreSettingsStorage(
                    PreferenceDataStoreFactory.create {
                        context.preferencesDataStoreFile("settings")
                    }
                )
            // Before the first frame, so a chosen theme doesn't flash the
            // default. A few hundred bytes from disk.
            val initial = runBlocking(Dispatchers.IO) { storage.load() }
            return AppGraph(
                appScope = scope,
                settings = SettingsController(initial, storage, scope),
            )
        }
    }
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("No AppGraph provided") }

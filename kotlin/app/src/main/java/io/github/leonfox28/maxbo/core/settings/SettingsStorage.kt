package io.github.leonfox28.maxbo.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

interface SettingsStorage {
    suspend fun load(): AppSettings

    suspend fun save(settings: AppSettings)
}

class DataStoreSettingsStorage(private val store: DataStore<Preferences>) : SettingsStorage {
    override suspend fun load(): AppSettings {
        val prefs = store.data.first()
        val defaults = AppSettings()
        val limit = prefs[cacheLimit]
        return AppSettings(
            themeMode = ThemeMode.entries.byKey(prefs[theme], ThemeMode::key, defaults.themeMode),
            textSize = TextSize.entries.byKey(prefs[textSize], TextSize::key, defaults.textSize),
            imageQuality =
                ImageQuality.entries.byKey(
                    prefs[quality],
                    ImageQuality::key,
                    defaults.imageQuality,
                ),
            imageCacheLimitMb =
                if (limit in AppSettings.imageCacheLimitChoices) limit!!
                else defaults.imageCacheLimitMb,
        )
    }

    override suspend fun save(settings: AppSettings) {
        store.edit {
            it[theme] = settings.themeMode.key
            it[textSize] = settings.textSize.key
            it[quality] = settings.imageQuality.key
            it[cacheLimit] = settings.imageCacheLimitMb
        }
    }

    private fun <T> List<T>.byKey(key: String?, keyOf: (T) -> String, fallback: T): T =
        firstOrNull { keyOf(it) == key } ?: fallback

    private companion object {
        val theme = stringPreferencesKey("settings.theme")
        val textSize = stringPreferencesKey("settings.textSize")
        val quality = stringPreferencesKey("settings.imageQuality")
        val cacheLimit = intPreferencesKey("settings.imageCacheLimitMb")
    }
}

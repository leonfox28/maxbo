package io.github.leonfox28.maxbo.support

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

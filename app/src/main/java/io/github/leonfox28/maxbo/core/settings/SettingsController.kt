package io.github.leonfox28.maxbo.core.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The settings in effect, starting from those loaded at launch ([initial]). Saves run in [scope],
 * which outlives the page that made the change.
 */
class SettingsController(
    initial: AppSettings,
    private val storage: SettingsStorage,
    private val scope: CoroutineScope,
) {
    private val _settings = MutableStateFlow(initial)
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val saving = Mutex()

    /** Applies [change] at once and saves it. */
    fun update(change: (AppSettings) -> AppSettings): Job {
        _settings.update(change)
        // One save at a time, each of the latest settings, so the last to
        // run stores the last change.
        return scope.launch { saving.withLock { storage.save(_settings.value) } }
    }
}

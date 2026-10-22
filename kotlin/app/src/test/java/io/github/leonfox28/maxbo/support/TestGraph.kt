package io.github.leonfox28.maxbo.support

import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.core.settings.AppSettings
import io.github.leonfox28.maxbo.core.settings.SettingsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** An [AppGraph] of fakes, as `signedInOverrides()` gave the Flutter tests. */
fun testGraph(
    settings: AppSettings = AppSettings(),
    settingsStorage: InMemorySettingsStorage = InMemorySettingsStorage(settings),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
): AppGraph =
    AppGraph(appScope = scope, settings = SettingsController(settings, settingsStorage, scope))

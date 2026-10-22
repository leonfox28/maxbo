package io.github.leonfox28.maxbo.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsStorageTest {
    @get:Rule val folder = TemporaryFolder()

    private fun TestScope.store(file: File = File(folder.root, "settings.preferences_pb")) =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

    @Test
    fun `starts from the defaults`() = runTest {
        assertEquals(AppSettings(), DataStoreSettingsStorage(store()).load())
    }

    @Test
    fun `reads back what it saved`() = runTest {
        val storage = DataStoreSettingsStorage(store())
        val settings =
            AppSettings(
                themeMode = ThemeMode.Dark,
                textSize = TextSize.ExtraSmall,
                imageQuality = ImageQuality.Original,
                imageCacheLimitMb = 1024,
            )
        storage.save(settings)
        assertEquals(settings, storage.load())
    }

    @Test
    fun `falls back to the default for what it doesn't know`() = runTest {
        val store = store()
        store.edit {
            it[stringPreferencesKey("settings.theme")] = "sepia"
            it[stringPreferencesKey("settings.textSize")] = "large"
            it[intPreferencesKey("settings.imageCacheLimitMb")] = 42
        }
        assertEquals(AppSettings(textSize = TextSize.Large), DataStoreSettingsStorage(store).load())
    }
}

package dev.pedalmate.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dev.pedalmate.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OverlayPrefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: OverlayPrefs

    @Before fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        prefs = OverlayPrefs(SettingsStore(dataStore))
    }

    @After fun tearDown() { scope.cancel() }

    @Test fun defaults() = runBlocking {
        assertEquals(OverlayPrefsState(false, null), prefs.load())
    }

    @Test fun minimizedPersists() = runBlocking {
        prefs.setMinimized(true)
        assertEquals(true, prefs.load().minimized)
    }

    @Test fun positionPersists() = runBlocking {
        prefs.setPosition(OverlayPosition(100, 200))
        assertEquals(OverlayPosition(100, 200), prefs.load().position)
    }

    @Test fun onlyXGivesNullPosition() = runBlocking {
        dataStore.edit { it[intPreferencesKey("overlayX")] = 100 }
        assertEquals(null, prefs.load().position)
    }
}

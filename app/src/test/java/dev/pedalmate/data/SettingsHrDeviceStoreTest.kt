package dev.pedalmate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import dev.pedalmate.heartrate.SavedHrDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsHrDeviceStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var settings: SettingsStore

    @Before fun setUp() {
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") })
    }

    @After fun tearDown() { scope.cancel() }

    @Test fun `loads the persisted device after start`() = runBlocking {
        settings.setHrDevice("AA:BB", "HR-1")
        val store = SettingsHrDeviceStore(settings, scope)
        withTimeout(2_000) { while (store.load() != SavedHrDevice("AA:BB", "HR-1")) delay(10) }
        assertEquals(SavedHrDevice("AA:BB", "HR-1"), store.load())
    }

    @Test fun `save is visible immediately and persisted`() = runBlocking {
        val store = SettingsHrDeviceStore(settings, scope)
        store.save(SavedHrDevice("CC:DD", "X"))
        assertEquals(SavedHrDevice("CC:DD", "X"), store.load())
        val s = withTimeout(2_000) { settings.settings.first { it.hrAddress == "CC:DD" } }
        assertEquals("X", s.hrName)
    }

    @Test fun `save null clears both`() = runBlocking {
        settings.setHrDevice("AA:BB", "HR-1")
        val store = SettingsHrDeviceStore(settings, scope)
        store.save(null)
        assertNull(store.load())
        val s = withTimeout(2_000) { settings.settings.first { it.hrAddress == null } }
        assertNull(s.hrName)
    }

    @Test fun `unrelated settings emission before the write lands does not flicker back to the old device`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val backing = MutableStateFlow<Preferences>(mutablePreferencesOf(stringPreferencesKey("hrAddress") to "AA:BB", stringPreferencesKey("hrName") to "A"))
        val gated = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = backing
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                gate.await()
                return transform(backing.value).also { backing.value = it }
            }
        }
        val store = SettingsHrDeviceStore(SettingsStore(gated), scope)
        withTimeout(2_000) { while (store.load() != SavedHrDevice("AA:BB", "A")) delay(10) }

        store.save(SavedHrDevice("CC:DD", "B"))
        // Unrelated FTP edit lands first and still carries the old device A.
        backing.value = mutablePreferencesOf(
            stringPreferencesKey("hrAddress") to "AA:BB", stringPreferencesKey("hrName") to "A", intPreferencesKey("ftp") to 250,
        )
        delay(300)
        assertEquals(SavedHrDevice("CC:DD", "B"), store.load())

        gate.complete(Unit)
        withTimeout(2_000) { while (backing.value[stringPreferencesKey("hrAddress")] != "CC:DD") delay(10) }
        delay(100)
        assertEquals(SavedHrDevice("CC:DD", "B"), store.load())
    }
}

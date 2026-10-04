package dev.pedalmate.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
}

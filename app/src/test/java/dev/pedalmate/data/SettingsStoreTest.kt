package dev.pedalmate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore

    @Before fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        store = SettingsStore(dataStore)
    }

    @After fun tearDown() { scope.cancel() }

    private suspend fun now() = store.settings.first()

    @Test fun defaults() = runBlocking {
        val s = now()
        assertNull(s.ftp); assertNull(s.lastWorkoutId)
        assertEquals("org.lichess.mobileV2", s.lichessPackage)
        assertFalse(s.overlayMinimized); assertNull(s.overlayX); assertNull(s.overlayY); assertNull(s.overlayW); assertNull(s.overlayH)
        assertNull(s.hrAddress); assertNull(s.hrName)
    }

    @Test fun `ftp set and clear`() = runBlocking {
        store.setFtp(200); assertEquals(200, now().ftp)
        store.setFtp(null); assertNull(now().ftp)
    }

    @Test fun `invalid ftp throws and keeps old value`() = runBlocking {
        store.setFtp(200)
        for (bad in listOf(49, 601)) {
            try { store.setFtp(bad); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(200, now().ftp)
    }

    @Test fun `corrupt stored ftp reads as null`() = runBlocking {
        dataStore.edit { it[intPreferencesKey("ftp")] = 9999 }
        assertNull(now().ftp)
    }

    @Test fun `lichess package round trips and blank throws`() = runBlocking {
        store.setLichessPackage("org.lichess.mobileapp")
        assertEquals("org.lichess.mobileapp", now().lichessPackage)
        try { store.setLichessPackage("  "); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
        assertEquals("org.lichess.mobileapp", now().lichessPackage)
    }

    @Test fun `overlay minimized and position`() = runBlocking {
        store.setOverlayMinimized(true); assertTrue(now().overlayMinimized)
        store.setOverlayPosition(100, 200, 557, 360)
        assertEquals(100, now().overlayX); assertEquals(200, now().overlayY)
        assertEquals(557, now().overlayW); assertEquals(360, now().overlayH)
    }

    @Test fun `hr device set and clear`() = runBlocking {
        store.setHrDevice("AA:BB", "HR-1")
        assertEquals("AA:BB", now().hrAddress); assertEquals("HR-1", now().hrName)
        store.setHrDevice(null, null)
        assertNull(now().hrAddress); assertNull(now().hrName)
    }

    @Test fun `last workout id`() = runBlocking {
        store.setLastWorkoutId("pz-43"); assertEquals("pz-43", now().lastWorkoutId)
    }
}

package dev.pedalmate.ui.setup

import dev.pedalmate.data.SettingsStore
import dev.pedalmate.ride.RideSnapshot
import dev.pedalmate.ride.RideStatus
import dev.pedalmate.testutil.InMemoryPreferencesDataStore
import dev.pedalmate.testutil.MemAssetReader
import dev.pedalmate.workout.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private class FakeCommands : RideCommands {
        val started = mutableListOf<String?>()
        var stopped = 0
        var paused = 0
        var resumed = 0
        var skipped = 0
        override fun start(workoutId: String?) { started += workoutId }
        override fun stop() { stopped++ }
        override fun pause() { paused++ }
        override fun resume() { resumed++ }
        override fun skip() { skipped++ }
    }

    private val t1 = """{"id":"t1","name":"Test One","description":"First","steps":[{"label":"Warm","seconds":600,"zone":2},{"label":"Hard","seconds":600,"zone":4}]}"""
    private val t2 = """{"id":"t2","name":"Test Two","steps":[{"label":"Go","seconds":600,"zone":3}]}"""
    private val files = mutableMapOf("workouts/t1.json" to t1, "workouts/t2.json" to t2)

    private val settings = SettingsStore(InMemoryPreferencesDataStore())
    private val snapshot = MutableStateFlow(RideSnapshot.idle(null))
    private val commands = FakeCommands()
    private var installed: Set<String> = setOf("org.lichess.mobileV2")

    private fun vm(repoFiles: Map<String, String> = files) =
        SetupViewModel(settings, WorkoutRepository(MemAssetReader(repoFiles)), snapshot, commands) { it in installed }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial state before settings load`() = runTest {
        val s = vm().state.value
        if (!s.loaded) assertEquals("Loading settings", s.startBlockedReason)
        assertEquals(s.startBlockedReason == null, s.canStart)
    }

    @Test
    fun `saved FTP shows with zone preview`() = runTest {
        settings.setFtp(250)
        val s = vm().state.value
        assertEquals("250", s.ftpText)
        assertNull(s.ftpError)
        assertEquals(7, s.zoneRows.size)
        assertEquals(ZoneRow(5, "263\u2013299 W"), s.zoneRows[4])
        assertEquals(ZoneRow(7, "\u2265 375 W"), s.zoneRows[6])
        assertEquals(ZoneRow(1, "0\u2013137 W"), s.zoneRows[0])
        assertNull(s.startWarning)
        assertTrue(s.canStart)
    }

    @Test
    fun `no FTP`() = runTest {
        val s = vm().state.value
        assertEquals("", s.ftpText)
        assertNull(s.ftpError)
        assertTrue(s.zoneRows.isEmpty())
        assertTrue(s.canStart)
        assertEquals("No FTP set: zones and targets will show dashes.", s.startWarning)
    }

    @Test
    fun `typing a valid FTP saves it`() = runTest {
        val v = vm()
        v.onFtpTextChanged("200")
        assertEquals(200, settings.settings.first().ftp)
        assertEquals("110\u2013149 W", v.state.value.zoneRows[1].rangeText)
    }

    @Test
    fun `typing an invalid FTP keeps the saved value`() = runTest {
        settings.setFtp(250)
        val v = vm()
        for ((text, err) in listOf("49" to FtpInput.RANGE_ERROR, "601" to FtpInput.RANGE_ERROR, "abc" to FtpInput.FORMAT_ERROR, "20.5" to FtpInput.FORMAT_ERROR)) {
            v.onFtpTextChanged(text)
            val s = v.state.value
            assertEquals(err, s.ftpError)
            assertEquals(250, settings.settings.first().ftp)
            assertEquals(text, s.ftpText)
            assertTrue(s.zoneRows.isEmpty())
            assertFalse(s.canStart)
            assertEquals("Fix the FTP value first", s.startBlockedReason)
        }
    }

    @Test
    fun `clearing the field clears FTP`() = runTest {
        settings.setFtp(250)
        val v = vm()
        v.onFtpTextChanged("")
        assertNull(settings.settings.first().ftp)
        assertNull(v.state.value.ftpError)
        assertNotNull(v.state.value.startWarning)
    }

    @Test
    fun `typing through intermediate values`() = runTest {
        val v = vm()
        v.onFtpTextChanged("6")
        v.onFtpTextChanged("60")
        assertEquals(60, settings.settings.first().ftp)
        v.onFtpTextChanged("600")
        assertEquals(600, settings.settings.first().ftp)
    }

    @Test
    fun `workouts list`() = runTest {
        val w = vm().state.value.workouts
        assertEquals(listOf("t1", "t2", FREE_RIDE_ID), w.map { it.id })
        assertEquals("20 min - First", w[0].subtitle)
        assertEquals("10 min", w[1].subtitle)
        assertEquals("Free ride", w[2].title)
    }

    @Test
    fun `initial selection`() = runTest {
        assertEquals("t1", vm().state.value.selectedId)
        settings.setLastWorkoutId("t2")
        assertEquals("t2", vm().state.value.selectedId)
        settings.setLastWorkoutId("gone")
        assertEquals("t1", vm().state.value.selectedId)
        settings.setLastWorkoutId(FREE_RIDE_ID)
        assertEquals(FREE_RIDE_ID, vm().state.value.selectedId)
        assertEquals(FREE_RIDE_ID, vm(emptyMap()).state.value.selectedId)
    }

    @Test
    fun `select`() = runTest {
        val v = vm()
        v.select("t2")
        assertEquals("t2", v.state.value.selectedId)
        assertEquals("t2", settings.settings.first().lastWorkoutId)
        v.select("nope")
        assertEquals("t2", v.state.value.selectedId)
    }

    @Test
    fun `load errors`() = runTest {
        val s = vm(files + ("workouts/bad.json" to "{not json")).state.value
        assertEquals(1, s.loadErrors.size)
        assertTrue(s.loadErrors[0].startsWith("bad.json: "))
        assertEquals(listOf("t1", "t2", FREE_RIDE_ID), s.workouts.map { it.id })
    }

    @Test
    fun `start workout`() = runTest {
        settings.setFtp(250)
        val v = vm()
        v.select("t2")
        assertEquals(StartDecision.Started("org.lichess.mobileV2"), v.start())
        assertEquals(listOf<String?>("t2"), commands.started)
        assertEquals("t2", settings.settings.first().lastWorkoutId)
        assertNull(v.state.value.message)
    }

    @Test
    fun `start free ride`() = runTest {
        val v = vm()
        v.select(FREE_RIDE_ID)
        v.start()
        assertEquals(listOf<String?>(null), commands.started)
    }

    @Test
    fun `start without Lichess installed`() = runTest {
        installed = emptySet()
        val v = vm()
        assertEquals(StartDecision.Started(null), v.start())
        assertEquals(1, commands.started.size)
        val m = v.state.value.message!!
        assertTrue(m.contains("Lichess not found"))
        assertTrue(m.contains("pm list packages"))
    }

    @Test
    fun `start falls back to the old package`() = runTest {
        installed = setOf("org.lichess.mobileapp")
        assertEquals(StartDecision.Started("org.lichess.mobileapp"), vm().start())
    }

    @Test
    fun `start blocked by a bad FTP`() = runTest {
        val v = vm()
        v.onFtpTextChanged("49")
        assertEquals(StartDecision.Blocked("Fix the FTP value first"), v.start())
        assertTrue(commands.started.isEmpty())
        assertEquals("Fix the FTP value first", v.state.value.message)
        v.dismissMessage()
        assertNull(v.state.value.message)
    }

    @Test
    fun `start blocked while a ride runs`() = runTest {
        val v = vm()
        for (st in listOf(RideStatus.RUNNING, RideStatus.PAUSED)) {
            snapshot.value = RideSnapshot.idle(250).copy(status = st)
            assertFalse(v.state.value.canStart)
            assertEquals(StartDecision.Blocked("A ride is already running"), v.start())
        }
        assertTrue(commands.started.isEmpty())
        snapshot.value = RideSnapshot.idle(250).copy(status = RideStatus.FINISHED)
        assertTrue(v.state.value.canStart)
    }

    @Test
    fun `ride controls pass through`() = runTest {
        val v = vm()
        v.stop(); v.pause(); v.resume(); v.skip()
        assertEquals(1, commands.stopped)
        assertEquals(1, commands.paused)
        assertEquals(1, commands.resumed)
        assertEquals(1, commands.skipped)
    }

    @Test
    fun `Lichess package`() = runTest {
        val v = vm()
        assertEquals("org.lichess.mobileV2", v.state.value.lichessPackage)
        v.onLichessPackageChanged("org.lichess.mobileapp")
        assertEquals("org.lichess.mobileapp", settings.settings.first().lichessPackage)
        v.onLichessPackageChanged("  ")
        assertEquals("Package name required", v.state.value.lichessPackageError)
        assertEquals("org.lichess.mobileapp", settings.settings.first().lichessPackage)
        v.onLichessPackageChanged(" org.x ")
        assertEquals("org.x", settings.settings.first().lichessPackage)
    }

    @Test
    fun `HR label`() = runTest {
        val v = vm()
        assertNull(v.state.value.hrDeviceLabel)
        settings.setHrDevice("AA:BB", "HR-1")
        assertEquals("HR-1 (AA:BB)", v.state.value.hrDeviceLabel)
        settings.setHrDevice("AA:BB", null)
        assertEquals("AA:BB", v.state.value.hrDeviceLabel)
    }

    @Test
    fun `no FTP never blocks a free ride`() = runTest {
        val v = vm()
        v.select(FREE_RIDE_ID)
        assertTrue(v.start() is StartDecision.Started)
    }

    @Test
    fun `launch button`() = runTest {
        val v = vm()
        assertEquals("org.lichess.mobileV2", v.onLaunchLichessRequested())
        assertNull(v.state.value.message)
        installed = emptySet()
        assertNull(v.onLaunchLichessRequested())
        assertTrue(v.state.value.message!!.contains("Lichess not found"))
    }

    @Test
    fun `showMessage publishes and dismiss clears`() = runTest {
        val v = vm()
        v.showMessage("hello")
        assertEquals("hello", v.state.value.message)
        v.dismissMessage()
        assertNull(v.state.value.message)
    }
}

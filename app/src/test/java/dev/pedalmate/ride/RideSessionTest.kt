package dev.pedalmate.ride

import dev.pedalmate.audio.Cue
import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.testutil.*
import dev.pedalmate.workout.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private const val WORKOUT =
    """{"id":"t","name":"Test","steps":[{"label":"Warm","seconds":10,"zone":2},{"label":"Hard","seconds":10,"zone":4}]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class RideSessionTest {
    private val bike = FakeBike()
    private val hr = FakeHr()
    private val log = RecordingLog()
    private val cues = RecordingCues()
    private var ftp: Int? = 200      // FTP 200: Z1 0-109, Z2 110-149, Z3 150-179, Z4 180-209
    private val repo = WorkoutRepository(MemAssetReader(mapOf("workouts/t.json" to WORKOUT)))

    private fun TestScope.newSession(ready: suspend () -> Unit = {}) =
        RideSession(SensorHub(bike, hr), repo, log, cues, { ftp }, backgroundScope, ready)

    /** Ticks every 250 ms from [fromMs] to [toMs] inclusive. */
    private fun RideSession.run(fromMs: Long, toMs: Long) { var t = fromMs; while (t <= toMs) { tick(t); t += 250 } }

    @Test fun `idle until started and ticks while idle are harmless`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession()
        s.tick(1_000)
        assertEquals(RideStatus.IDLE, s.snapshot.value.status)
        assertNull(s.snapshot.value.workout); assertFalse(s.isActive)
    }

    @Test fun `startWorkout acquires sensors begins the log and runs the engine`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession()
        assertEquals(StartResult.Started, s.startWorkout("t"))
        assertEquals(1, bike.started); assertEquals(1, hr.started)
        assertEquals(listOf<Pair<String?, Int?>>("t" to 200), log.begins)
        val snap = s.snapshot.value
        assertEquals(RideStatus.RUNNING, snap.status); assertEquals("Test", snap.workoutName)
        assertEquals("Warm", snap.workout!!.currentStep!!.label); assertEquals(200, snap.ftp)
        assertTrue(s.isActive)
    }

    @Test fun `snapshot carries live metrics zone and target`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t")
        bike.set(power = 130, cadence = 80, resistance = 30)
        s.run(0, 4_000)
        val snap = s.snapshot.value
        assertEquals(130, snap.powerWatts); assertEquals(130, snap.smoothedPowerWatts)
        assertEquals(80, snap.cadenceRpm); assertEquals(30, snap.resistancePercent)
        assertEquals(PowerZone.Z2, snap.currentZone); assertEquals(PowerZone.Z2, snap.targetZone)
        assertEquals(110, snap.targetRange!!.lowWatts); assertEquals(149, snap.targetRange!!.highWatts)
        assertEquals(TargetStatus.IN, snap.targetStatus)
        assertEquals(4_000L, snap.workout!!.stepElapsedMs); assertEquals(4_000L, snap.elapsedMs)
    }

    @Test fun `snapshot carries live average and max power from the ride log`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession()
        assertNull(s.snapshot.value.avgPowerWatts); assertNull(s.snapshot.value.maxPowerWatts)
        s.startWorkout("t")
        bike.set(power = 100); s.tick(0)
        bike.set(power = 200); s.tick(250)
        s.tick(500)
        assertEquals(150, s.snapshot.value.avgPowerWatts); assertEquals(200, s.snapshot.value.maxPowerWatts)
        s.stop()
        assertNull(s.snapshot.value.avgPowerWatts); assertNull(s.snapshot.value.maxPowerWatts)
    }

    @Test fun `power below the target range reports BELOW and the lower zone`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); bike.set(power = 100)
        s.run(0, 8_000)
        assertEquals(PowerZone.Z1, s.snapshot.value.currentZone)
        assertEquals(PowerZone.Z2, s.snapshot.value.targetZone)
        assertEquals(TargetStatus.BELOW, s.snapshot.value.targetStatus)
    }

    @Test fun `a dropped sensor never shows stale numbers`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); bike.set(power = 130)
        s.run(0, 3_000)
        assertEquals(130, s.snapshot.value.smoothedPowerWatts)
        bike.stateFlow.value = ConnectionState.Disconnected      // metrics keep the stale 130 / 80 / 30
        s.tick(3_250)
        val d = s.snapshot.value
        assertEquals(ConnectionState.Disconnected, d.bikeState)
        assertNull(d.powerWatts); assertNull(d.smoothedPowerWatts); assertNull(d.cadenceRpm); assertNull(d.resistancePercent)
        assertNull(d.currentZone); assertNull(d.targetStatus)
        assertNotNull(d.targetRange); assertEquals(RideStatus.RUNNING, d.status)   // the workout keeps going
        assertNull(log.frames.last().powerWatts); assertNull(log.frames.last().zone); assertNull(log.frames.last().cadenceRpm)
        bike.stateFlow.value = ConnectionState.Unavailable; s.tick(3_500)
        assertNull(s.snapshot.value.powerWatts)
        bike.set(power = 200); s.tick(3_750)                      // reconnect: old 130 must not be averaged in
        assertEquals(200, s.snapshot.value.smoothedPowerWatts)
    }

    @Test fun `heart rate is null unless the HR source is connected`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); bike.set(power = 130)
        hr.bpmFlow.value = 150; hr.stateFlow.value = ConnectionState.Connected
        s.tick(0); assertEquals(150, s.snapshot.value.heartRateBpm)
        hr.stateFlow.value = ConnectionState.Disconnected          // bpm flow still holds 150
        s.tick(250); assertNull(s.snapshot.value.heartRateBpm)
    }

    @Test fun `no FTP means no zone range or status but the workout runs`() = runTest(UnconfinedTestDispatcher()) {
        ftp = null
        val s = newSession(); assertEquals(StartResult.Started, s.startWorkout("t")); bike.set(power = 130)
        s.run(0, 3_000)
        val snap = s.snapshot.value
        assertNull(snap.ftp); assertNull(snap.currentZone); assertNull(snap.targetRange); assertNull(snap.targetStatus)
        assertEquals(PowerZone.Z2, snap.targetZone)                // the step still names its zone
        assertEquals(130, snap.powerWatts); assertEquals(3_000L, snap.workout!!.stepElapsedMs)
        s.tick(1_000_000); assertEquals(RideStatus.FINISHED, s.snapshot.value.status)
    }

    @Test fun `a huge delta plays every interval cue in order and finishes the ride once`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); bike.set(power = 130)
        s.tick(0); s.tick(1_000_000)
        assertEquals(RideStatus.FINISHED, s.snapshot.value.status)
        assertEquals(listOf(Cue.START, Cue.STEP_HARDER, Cue.FINISH), cues.played)
        assertEquals(1, log.finishes)
        val before = log.frames.size; s.tick(1_000_250)
        assertEquals(before, log.frames.size)                        // nothing offered after FINISHED
    }

    @Test fun `a negative delta is ignored and the clock keeps moving`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); bike.set(power = 130)
        s.tick(5_000); s.tick(4_000)
        assertEquals(0L, s.snapshot.value.workout!!.stepElapsedMs)
        s.tick(5_000)
        assertEquals(1_000L, s.snapshot.value.workout!!.stepElapsedMs)
    }

    @Test fun `countdown cues fire while ticking at 250 ms`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); bike.set(power = 130)
        s.run(0, 10_000)
        assertEquals(listOf(Cue.START, Cue.COUNTDOWN, Cue.COUNTDOWN, Cue.COUNTDOWN, Cue.STEP_HARDER), cues.played)
    }

    @Test fun `engine events are forwarded for the toast`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession()
        val seen = mutableListOf<WorkoutEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { s.events.collect { seen += it } }
        s.startWorkout("t"); s.tick(0); s.tick(10_000)
        assertTrue(seen[0] is WorkoutEvent.Started)
        val change = seen[1] as WorkoutEvent.IntervalChanged
        assertEquals("Warm", change.from.label); assertEquals("Hard", change.to.label)
        assertEquals(2, seen.size)
    }

    @Test fun `pause freezes the workout and stops recording until resume`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); bike.set(power = 130)
        s.tick(0); s.tick(2_000)
        s.pause(); assertEquals(RideStatus.PAUSED, s.snapshot.value.status); assertTrue(s.isActive)
        val frames = log.frames.size
        s.tick(5_000)
        assertEquals(2_000L, s.snapshot.value.workout!!.stepElapsedMs); assertEquals(frames, log.frames.size)
        s.resume(); s.tick(6_000)
        assertEquals(RideStatus.RUNNING, s.snapshot.value.status)
        assertEquals(3_000L, s.snapshot.value.workout!!.stepElapsedMs)
    }

    @Test fun `skip moves to the next interval and skipping the last one finishes`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); bike.set(power = 130)
        s.tick(0); s.skip()
        assertEquals(1, s.snapshot.value.workout!!.stepIndex); assertEquals(Cue.STEP_HARDER, cues.played.last())
        s.skip(); s.tick(250)
        assertEquals(RideStatus.FINISHED, s.snapshot.value.status); assertEquals(1, log.finishes)
    }

    @Test fun `stop releases sensors finishes the ride and returns to idle`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); bike.set(power = 130); s.run(0, 1_000)
        s.stop()
        assertEquals(RideStatus.IDLE, s.snapshot.value.status); assertNull(s.snapshot.value.workout)
        assertEquals(1, bike.stopped); assertEquals(1, hr.stopped); assertEquals(1, log.finishes)
        s.stop()                                                      // idle stop is a harmless no-op
        assertEquals(1, log.finishes)
    }

    @Test fun `stopping a finished ride does not finish the log twice`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); s.tick(0); s.tick(1_000_000)
        s.stop()
        assertEquals(1, log.finishes); assertEquals(1, bike.stopped)
    }

    @Test fun `free ride counts elapsed time and has no target`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); assertEquals(StartResult.Started, s.startFreeRide()); bike.set(power = 190)
        s.run(0, 5_000)
        val snap = s.snapshot.value
        assertNull(snap.workout); assertNull(snap.targetRange); assertNull(snap.targetStatus)
        assertEquals(5_000L, snap.elapsedMs); assertEquals(PowerZone.Z4, snap.currentZone)
        assertEquals(listOf<Pair<String?, Int?>>(null to 200), log.begins)
        s.pause(); assertEquals(RideStatus.RUNNING, s.snapshot.value.status)   // free ride ignores pause
        assertTrue(cues.played.isEmpty())
    }

    @Test fun `unknown workout and double start are rejected without side effects`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession()
        assertEquals(StartResult.UnknownWorkout, s.startWorkout("nope"))
        assertEquals(0, bike.started); assertTrue(log.begins.isEmpty())
        s.startWorkout("t")
        assertEquals(StartResult.AlreadyRunning, s.startWorkout("t"))
        assertEquals(StartResult.AlreadyRunning, s.startFreeRide())
        assertEquals(1, bike.started); assertEquals(1, log.begins.size)
    }

    @Test fun `start waits for the ready gate`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val s = newSession(ready = { gate.await() })
        val job = launch { s.startWorkout("t") }
        assertTrue(log.begins.isEmpty()); assertEquals(RideStatus.IDLE, s.snapshot.value.status)
        gate.complete(Unit); job.join()
        assertEquals(1, log.begins.size); assertEquals(RideStatus.RUNNING, s.snapshot.value.status)
    }

    @Test fun `stop during a suspended begin leaves no running zombie`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val s = newSession(ready = { gate.await() })
        var result: StartResult? = null
        val job = launch { result = s.startWorkout("t") }
        s.stop()                                   // STOP arrives while begin() is suspended on the ready gate
        gate.complete(Unit); job.join()
        assertEquals(StartResult.Stopped, result)
        assertEquals(RideStatus.IDLE, s.snapshot.value.status); assertFalse(s.isActive)
        assertEquals(bike.started, bike.stopped); assertEquals(hr.started, hr.stopped)   // hub balanced
        assertEquals(log.begins.size, log.finishes)                                      // no begun-but-unfinished ride
        assertEquals(StartResult.Started, s.startWorkout("t"))                           // flag does not leak into the next start
        assertEquals(RideStatus.RUNNING, s.snapshot.value.status)
    }

    @Test fun `sensors are acquired before the first suspension`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val s = newSession(ready = { gate.await() })
        val job = launch { s.startWorkout("t") }
        assertEquals(1, bike.started); assertEquals(1, hr.started)     // held while the ready gate is still closed
        assertEquals(RideStatus.IDLE, s.snapshot.value.status)
        gate.complete(Unit); job.join()
        assertEquals(1, bike.started); assertEquals(0, bike.stopped)   // still one hold, acquired once
    }

    @Test fun `a failed start releases the early hold exactly once`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(ready = { throw IllegalStateException("boom") })
        try { s.startWorkout("t"); fail("expected the failure to propagate") } catch (e: IllegalStateException) { /* expected */ }
        assertEquals(1, bike.started); assertEquals(1, bike.stopped)
        assertFalse(s.isActive)
        s.stop()                                                        // a later stop must not double-release
        assertEquals(1, bike.stopped)
    }

    @Test fun `a rejected second start does not acquire`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t")
        s.startWorkout("t")
        assertEquals(1, bike.started)
    }

    @Test fun `a failing ride log does not stop the ride`() = runTest(UnconfinedTestDispatcher()) {
        log.failBegin = true
        val s = newSession()
        assertEquals(StartResult.Started, s.startWorkout("t"))
        assertEquals(RideStatus.RUNNING, s.snapshot.value.status)
    }

    @Test fun `a new ride can start after a finished one`() = runTest(UnconfinedTestDispatcher()) {
        val s = newSession(); s.startWorkout("t"); s.tick(0); s.tick(1_000_000)
        assertEquals(StartResult.Started, s.startWorkout("t"))
        assertEquals(RideStatus.RUNNING, s.snapshot.value.status)
        assertEquals(2, bike.started)
    }
}

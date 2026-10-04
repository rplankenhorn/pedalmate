package dev.pedalmate.workout

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutEngineTest {
    private val ftp200 = ZoneTable.forFtp(200)
    private fun def(vararg s: WorkoutStep) = WorkoutDefinition("t", "T", null, s.toList())
    /** 27 s total: Warm 10 s Z2, Hard 8 s Z4, Short 3 s Z3, Cool 6 s Z1. */
    private val four = def(
        WorkoutStep("Warm", 10, 2), WorkoutStep("Hard", 8, 4),
        WorkoutStep("Short", 3, 3), WorkoutStep("Cool", 6, 1),
    )

    private fun WorkoutEvent.tag() = when (this) {
        is WorkoutEvent.Started -> "S"
        is WorkoutEvent.IntervalChanged -> "I${from.index}>${to.index}"
        is WorkoutEvent.Countdown -> "C$seconds"
        is WorkoutEvent.Skipped -> "K$fromIndex"
        WorkoutEvent.Finished -> "F"
    }

    private fun TestScope.record(engine: WorkoutEngine): List<String> {
        val out = mutableListOf<String>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect { out += it.tag() } }
        return out
    }

    private fun WorkoutEngine.tick(totalMs: Long, stepMs: Long = 250) {
        var left = totalMs
        while (left > 0) { val d = minOf(stepMs, left); advance(d); left -= d }
    }

    @Test fun `start emits Started and runs step 0`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200)
        val events = record(e)
        assertEquals(WorkoutPhase.IDLE, e.state.value.phase)
        assertNull(e.state.value.currentStep)
        assertEquals("Warm", e.state.value.nextStep!!.label)
        e.start()
        val s = e.state.value
        assertEquals(listOf("S"), events)
        assertEquals(WorkoutPhase.RUNNING, s.phase)
        assertEquals(0, s.stepIndex); assertEquals(4, s.stepCount)
        assertEquals(10_000L, s.stepRemainingMs); assertEquals(27_000L, s.totalMs)
        assertEquals(ftp200!!.rangeOf(PowerZone.Z2), s.currentStep!!.wattRange)   // 110..149
        assertEquals("Hard", s.nextStep!!.label)
    }

    @Test fun `advance moves elapsed and remaining`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); e.start(); e.advance(4_000)
        val s = e.state.value
        assertEquals(4_000L, s.stepElapsedMs); assertEquals(6_000L, s.stepRemainingMs)
        assertEquals(4_000L, s.totalElapsedMs); assertEquals(23_000L, s.totalRemainingMs)
    }

    @Test fun `countdown fires at 3 2 1 then the interval changes`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); val events = record(e)
        e.start(); e.tick(10_000)
        assertEquals(listOf("S", "C3", "C2", "C1", "I0>1"), events)
    }

    @Test fun `short steps and the last step never count down`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); val events = record(e)
        e.start(); e.tick(27_000)
        assertEquals(
            listOf("S", "C3", "C2", "C1", "I0>1", "C3", "C2", "C1", "I1>2", "I2>3", "F"), events)
    }

    @Test fun `a 4 second step still counts down`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(def(WorkoutStep("A", 4, 1), WorkoutStep("B", 4, 2)), ftp200)
        val events = record(e); e.start(); e.tick(8_000)
        assertEquals(listOf("S", "C3", "C2", "C1", "I0>1", "F"), events)
    }

    @Test fun `pause freezes time and resume continues`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); e.start(); e.advance(4_000)
        e.pause(); e.advance(5_000)
        assertEquals(WorkoutPhase.PAUSED, e.state.value.phase); assertEquals(4_000L, e.state.value.stepElapsedMs)
        e.resume(); e.advance(1_000)
        assertEquals(WorkoutPhase.RUNNING, e.state.value.phase); assertEquals(5_000L, e.state.value.stepElapsedMs)
    }

    @Test fun `skip in the middle jumps to the next step`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); val events = record(e)
        e.start(); e.skip()
        assertEquals(listOf("S", "K0", "I0>1"), events)
        assertEquals(1, e.state.value.stepIndex); assertEquals(10_000L, e.state.value.totalElapsedMs)
        assertEquals(0L, e.state.value.stepElapsedMs)
    }

    @Test fun `skip on the last step finishes`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); val events = record(e)
        e.start(); repeat(4) { e.skip() }
        assertEquals(listOf("S", "K0", "I0>1", "K1", "I1>2", "K2", "I2>3", "K3", "F"), events)
        assertEquals(WorkoutPhase.FINISHED, e.state.value.phase)
    }

    @Test fun `huge delta emits every IntervalChanged in order and no countdown`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); val events = record(e)
        e.start(); e.advance(1_000_000)
        assertEquals(listOf("S", "I0>1", "I1>2", "I2>3", "F"), events)
        val s = e.state.value
        assertEquals(WorkoutPhase.FINISHED, s.phase); assertEquals(27_000L, s.totalElapsedMs)
        assertEquals(0L, s.stepRemainingMs); assertEquals(1f, s.progress, 0f)
        assertNull(s.currentStep); assertNull(s.nextStep)
    }

    @Test fun `stalled ticker lands mid-step without stale countdowns`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); val events = record(e)
        e.start(); e.advance(25_000)
        assertEquals(listOf("S", "I0>1", "I1>2", "I2>3"), events)
        val s = e.state.value
        assertEquals(3, s.stepIndex); assertEquals(4_000L, s.stepElapsedMs); assertEquals(2_000L, s.stepRemainingMs)
    }

    @Test fun `a long gap inside one step emits only the nearest countdown`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); val events = record(e)
        e.start(); e.advance(8_000)                    // 10 s step: remaining 10000 -> 2000 crosses 3 and 2
        assertEquals(listOf("S", "C2"), events)
    }

    @Test fun `zero negative and idle deltas are ignored`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); val events = record(e)
        e.advance(5_000); assertEquals(WorkoutPhase.IDLE, e.state.value.phase)
        e.start(); e.advance(0); e.advance(-500)
        assertEquals(0L, e.state.value.stepElapsedMs); assertEquals(listOf("S"), events)
    }

    @Test fun `progress never decreases and ends at 1`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); e.start()
        val seen = mutableListOf(e.state.value.progress)
        repeat(100) { e.advance(250); if (it == 20) e.skip(); seen += e.state.value.progress }
        assertEquals(seen.sorted(), seen); assertEquals(1f, seen.last(), 0f)
    }

    @Test fun `null FTP leaves ranges empty but the workout runs`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, null); e.start()
        assertEquals(PowerZone.Z2, e.state.value.currentStep!!.zone)
        assertNull(e.state.value.currentStep!!.wattRange)
        e.advance(1_000_000)
        assertEquals(WorkoutPhase.FINISHED, e.state.value.phase)
    }

    @Test fun `a step without a zone has neither zone nor range`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(def(WorkoutStep("FTP Test", 1200, null, "ftp-window")), ftp200); e.start()
        assertNull(e.state.value.currentStep!!.zone); assertNull(e.state.value.currentStep!!.wattRange)
    }

    @Test fun `start twice and resume while running are no-ops`() = runTest(UnconfinedTestDispatcher()) {
        val e = WorkoutEngine(four, ftp200); val events = record(e)
        e.start(); e.start(); e.resume()
        assertEquals(listOf("S"), events); assertEquals(WorkoutPhase.RUNNING, e.state.value.phase)
    }

    @Test fun `workout package has no Android imports`() {
        val dir = File("src/main/java/dev/pedalmate/workout")
        assertTrue(dir.isDirectory)
        dir.walkTopDown().filter { it.extension == "kt" }.forEach {
            assertFalse("${it.name} imports android", it.readText().contains("import android"))
        }
    }
}

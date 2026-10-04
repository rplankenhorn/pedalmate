package dev.pedalmate.sensor

import dev.pedalmate.testutil.FakeClock
import dev.pedalmate.testutil.FakeTickScheduler
import dev.pedalmate.testutil.TickHarness
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val M = BikeMetrics(cadenceRpm = 87, resistancePercent = 44, powerWatts = 183)

private class FakeSource : PollableBikeDataSource {
    override val metrics = MutableStateFlow(BikeMetrics.ZERO)
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override var framesReceived: Long = 0L
    var pollResult: BikeMetrics? = null
    var pollCount = 0
    var started = 0
    var stopped = 0

    override fun start() { started++ }
    override fun stop() { stopped++ }
    override fun pollBikeData(): BikeMetrics? {
        pollCount++
        val r = pollResult
        if (r != null) { framesReceived++; metrics.value = r }
        return r
    }
    /** Simulates one pushed frame (a StateFlow would drop equal values; the counter must not). */
    fun pushFrame(m: BikeMetrics = M) { framesReceived++; metrics.value = m }
}

class BikeSourceSupervisorTest {
    private val source = FakeSource()
    private val clock = FakeClock()
    private val scheduler = FakeTickScheduler()
    private val harness = TickHarness(clock, scheduler)
    private val supervisor = BikeSourceSupervisor(source, clock, scheduler)
    private val state get() = supervisor.connectionState.value

    @Test fun `start and stop drive source and scheduler`() {
        supervisor.start()
        assertEquals(1, source.started)
        assertTrue(scheduler.running)
        assertEquals(250L, scheduler.periodMs)
        supervisor.stop()
        assertEquals(1, source.stopped)
        assertFalse(scheduler.running)
        assertEquals(ConnectionState.Unavailable, state)
    }

    @Test fun `stays Unavailable until the first frame`() {
        supervisor.start()
        harness.advanceBy(1_000)
        assertEquals(ConnectionState.Unavailable, state)
        source.pushFrame()
        harness.advanceBy(250)
        assertEquals(ConnectionState.Connected, state)
    }

    @Test fun `identical consecutive frames stay Connected and never trigger polling`() {
        supervisor.start()
        repeat(40) { source.pushFrame(M); harness.advanceBy(250) }   // 10 s of the same reading
        assertEquals(ConnectionState.Connected, state)
        assertEquals(0, source.pollCount)
    }

    @Test fun `silent source is polled at 1 Hz starting 5 s after start`() {
        supervisor.start()
        harness.advanceBy(4_750)
        assertEquals(0, source.pollCount)
        harness.advanceBy(250)                  // t = 5000
        assertEquals(1, source.pollCount)
        harness.advanceBy(3_000)                // polls at 6000, 7000, 8000
        assertEquals(4, source.pollCount)
    }

    @Test fun `resumed push frames stop polling`() {
        supervisor.start()
        harness.advanceBy(6_000)                // polls at 5000 and 6000
        assertEquals(2, source.pollCount)
        source.pushFrame(); source.pushFrame()
        harness.advanceBy(250)                  // supervisor sees pushes, polling off
        val polls = source.pollCount
        harness.advanceBy(4_000)                // < 5 s since last push
        assertEquals(polls, source.pollCount)
    }

    @Test fun `3 s without any frame reports Disconnected`() {
        supervisor.start()
        source.pushFrame()
        harness.advanceBy(250)                  // t = 250, Connected
        assertEquals(ConnectionState.Connected, state)
        harness.advanceBy(2_750)                // t = 3000, gap 2750
        assertEquals(ConnectionState.Connected, state)
        harness.advanceBy(250)                  // t = 3250, gap 3000
        assertEquals(ConnectionState.Disconnected, state)
    }

    @Test fun `a successful poll restores Connected`() {
        supervisor.start()
        source.pushFrame()
        harness.advanceBy(3_250)                // Disconnected
        assertEquals(ConnectionState.Disconnected, state)
        source.pollResult = M
        harness.advanceBy(2_000)                // t = 5250: 5 s since last push, first poll succeeds
        assertEquals(ConnectionState.Connected, state)
    }

    @Test fun `a failing poll leaves Disconnected`() {
        supervisor.start()
        source.pushFrame()
        harness.advanceBy(10_000)
        assertEquals(ConnectionState.Disconnected, state)
        assertTrue(source.pollCount > 0)
    }

    @Test fun `restart after stop forgets the old session`() {
        supervisor.start()
        source.pushFrame()
        harness.advanceBy(250)
        supervisor.stop()
        supervisor.start()
        assertEquals(ConnectionState.Unavailable, state)
        harness.advanceBy(1_000)
        assertEquals(ConnectionState.Unavailable, state)
        source.pushFrame()
        harness.advanceBy(250)
        assertEquals(ConnectionState.Connected, state)
    }

    @Test fun `metrics pass through from the wrapped source`() {
        source.pushFrame(M)
        assertEquals(M, supervisor.metrics.value)
    }
}

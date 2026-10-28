package dev.pedalmate.sensor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockBikeDataSourceTest {
    @Test fun `unavailable until started then Connected with the first sample`() = runTest {
        val src = MockBikeDataSource(ScriptedProfile(seed = 1), backgroundScope)
        assertEquals(ConnectionState.Unavailable, src.connectionState.value)
        src.start(); runCurrent()
        assertEquals(ConnectionState.Connected, src.connectionState.value)
        assertEquals(ScriptedProfile(seed = 1).sample(0), src.metrics.value)
    }

    @Test fun `dropout flips to Disconnected and recovers`() = runTest {
        val src = MockBikeDataSource(ScriptedProfile(seed = 1), backgroundScope)
        src.start(); runCurrent()
        advanceTimeBy(140_000L); runCurrent()          // tick for t = 140 s
        assertEquals(ConnectionState.Disconnected, src.connectionState.value)
        advanceTimeBy(6_000L); runCurrent()            // t = 146 s
        assertEquals(ConnectionState.Connected, src.connectionState.value)
    }

    @Test fun `stop makes it Unavailable and silent`() = runTest {
        val src = MockBikeDataSource(ScriptedProfile(seed = 1), backgroundScope)
        src.start(); runCurrent()
        src.stop()
        val frozen = src.metrics.value
        advanceTimeBy(30_000L); runCurrent()
        assertEquals(ConnectionState.Unavailable, src.connectionState.value)
        assertEquals(frozen, src.metrics.value)
    }

    @Test fun `start twice leaves no orphaned loop running after stop`() = runTest {
        val src = MockBikeDataSource(ScriptedProfile(seed = 1), backgroundScope)
        src.start(); src.start(); runCurrent()
        src.stop()
        val frozen = src.metrics.value
        advanceTimeBy(30_000L); runCurrent()
        assertEquals(ConnectionState.Unavailable, src.connectionState.value)
        assertEquals(frozen, src.metrics.value)
    }

    @Test fun `after setResistance the frame holds that resistance and physics power`() = runTest {
        val src = MockBikeDataSource(ScriptedProfile(seed = 1, dropouts = false), backgroundScope)
        src.start(); runCurrent()
        assertTrue(src.setResistance(40))
        advanceTimeBy(250); runCurrent()
        val m = src.metrics.value
        assertEquals(40, m.resistancePercent)
        assertEquals(MockPhysics.watts(40, m.cadenceRpm), m.powerWatts)
    }

    @Test fun `forced resistance holds a flat 80 rpm through the scripted dropout`() = runTest {
        val src = MockBikeDataSource(ScriptedProfile(seed = 1), backgroundScope)
        src.start(); runCurrent()
        assertTrue(src.setResistance(40))
        var t = 0L
        while (t < 300_000L) {
            advanceTimeBy(250); runCurrent(); t += 250
            assertEquals(ConnectionState.Connected, src.connectionState.value)
            assertEquals(80, src.metrics.value.cadenceRpm)
            assertEquals(150, src.metrics.value.powerWatts)
        }
    }

    @Test fun `without a forced resistance the scripted cadence and dropout stay`() = runTest {
        val src = MockBikeDataSource(ScriptedProfile(seed = 1), backgroundScope)
        src.start(); runCurrent()
        advanceTimeBy(140_000L); runCurrent()
        assertEquals(ConnectionState.Disconnected, src.connectionState.value)
    }

    @Test fun `physics anchor is 150 W at 40 and 80 rpm`() { assertEquals(150, MockPhysics.watts(40, 80)) }
}

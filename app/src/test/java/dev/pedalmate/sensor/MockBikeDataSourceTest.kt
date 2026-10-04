package dev.pedalmate.sensor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Test fun `start twice does not double the tick rate`() = runTest {
        val src = MockBikeDataSource(ScriptedProfile(seed = 1), backgroundScope, tickMs = 1_000L)
        src.start(); src.start(); runCurrent()
        advanceTimeBy(10_000L); runCurrent()
        assertEquals(ScriptedProfile(seed = 1).sample(10), src.metrics.value)
    }
}

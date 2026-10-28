package dev.pedalmate.heartrate

import dev.pedalmate.sensor.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

@OptIn(ExperimentalCoroutinesApi::class)
class MockHeartRateDataSourceTest {
    @Test fun `profile is a sine around 110`() {
        assertEquals(110, MockHeartRateProfile.bpmAt(0))
        assertEquals(130, MockHeartRateProfile.bpmAt(30))
        assertEquals(90, MockHeartRateProfile.bpmAt(90))
        for (t in 0..600) assertTrue(MockHeartRateProfile.bpmAt(t) in 90..130)
    }

    @Test fun `source follows the profile and stops cleanly`() = runTest {
        val src = MockHeartRateDataSource(backgroundScope)
        assertEquals(ConnectionState.Unavailable, src.connectionState.value)
        assertNull(src.bpm.value)
        src.start(); runCurrent()
        assertEquals(ConnectionState.Connected, src.connectionState.value)
        assertEquals(110, src.bpm.value)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(130, src.bpm.value)
        src.stop()
        assertEquals(ConnectionState.Unavailable, src.connectionState.value)
        assertNull(src.bpm.value)
    }

    @Test fun `physiology lags toward 60 plus 0_45 watts`() = runTest {
        val watts = 200
        val src = MockHeartRateDataSource(backgroundScope, powerWatts = { watts })
        src.start(); runCurrent()
        val first = src.bpm.value!!
        advanceTimeBy(40_000); runCurrent()
        val at40 = src.bpm.value!!
        assertTrue(at40 > first); assertTrue(at40 < MockPhysiology.targetBpm(200))
        advanceTimeBy(200_000); runCurrent()
        assertEquals(MockPhysiology.targetBpm(200).toDouble(), src.bpm.value!!.toDouble(), 1.0)
    }

    @Test fun `physiology step is a first order lag`() {
        assertEquals(100.0 + 50.0 * (1 - exp(-1.0)), MockPhysiology.step(100.0, 150, 40.0), 1e-6)
    }
}

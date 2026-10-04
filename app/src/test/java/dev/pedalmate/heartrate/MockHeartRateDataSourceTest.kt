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
}

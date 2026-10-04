package dev.pedalmate.ride

import dev.pedalmate.testutil.FakeBike
import dev.pedalmate.testutil.FakeHr
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorHubTest {
    private val bike = FakeBike()
    private val hr = FakeHr()
    private val hub = SensorHub(bike, hr)

    @Test fun `ref counted start and stop`() {
        hub.acquire(); hub.acquire()
        assertEquals(1, bike.started); assertEquals(1, hr.started)
        hub.release()
        assertEquals(0, bike.stopped); assertEquals(0, hr.stopped)
        hub.release()
        assertEquals(1, bike.stopped); assertEquals(1, hr.stopped)
        hub.release()
        assertEquals(1, bike.stopped); assertEquals(1, hr.stopped)
        hub.acquire()
        assertEquals(2, bike.started); assertEquals(2, hr.started)
    }
}

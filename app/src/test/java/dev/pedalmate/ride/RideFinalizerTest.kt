package dev.pedalmate.ride

import dev.pedalmate.data.RideEntity
import dev.pedalmate.data.SampleEntity
import dev.pedalmate.testutil.FakeRideDao
import dev.pedalmate.testutil.FakeSampleDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RideFinalizerTest {
    private val start = 1_700_000_000_000L
    private val rides = FakeRideDao()
    private val samples = FakeSampleDao()
    private val finalizer = RideFinalizer(rides, samples)

    private suspend fun addRide(endedAt: Long? = null) = rides.insert(RideEntity(startedAt = start, endedAt = endedAt))

    @Test fun `unfinished ride is closed at its last sample`() = runBlocking {
        val id = addRide()
        samples.insertAll((0..30).map { SampleEntity(id, it, 100, 80, 30, 120, 2) })
        assertEquals(1, finalizer.finalizeUnfinished())
        val row = rides.byId(id)!!
        assertEquals(start + 30_000, row.endedAt); assertEquals(31, row.sampleCount)
        assertEquals(100, row.avgPowerWatts); assertEquals(100, row.maxPowerWatts)
        assertEquals(120, row.avgHeartRateBpm)
    }

    @Test fun `unfinished ride with no samples ends at start`() = runBlocking {
        val id = addRide()
        assertEquals(1, finalizer.finalizeUnfinished())
        val row = rides.byId(id)!!
        assertEquals(start, row.endedAt); assertEquals(0, row.sampleCount)
        assertNull(row.avgPowerWatts); assertNull(row.maxPowerWatts); assertNull(row.avgCadenceRpm)
        assertNull(row.avgResistancePercent); assertNull(row.avgHeartRateBpm)
    }

    @Test fun `finished ride is untouched`() = runBlocking {
        val id = addRide(endedAt = start + 5_000)
        val before = rides.byId(id)
        assertEquals(0, finalizer.finalizeUnfinished())
        assertEquals(0, rides.finishCalls); assertEquals(before, rides.byId(id))
    }

    @Test fun `one failing ride does not stop the others`() = runBlocking {
        val a = addRide(); val b = addRide()
        rides.failFinishForId = a
        assertEquals(1, finalizer.finalizeUnfinished())
        assertNotNull(rides.byId(b)!!.endedAt); assertNull(rides.byId(a)!!.endedAt)
    }

    @Test fun `second call finalises nothing`() = runBlocking {
        addRide()
        assertEquals(1, finalizer.finalizeUnfinished())
        assertEquals(0, finalizer.finalizeUnfinished())
    }
}

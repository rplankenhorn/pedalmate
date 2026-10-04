package dev.pedalmate.ride

import dev.pedalmate.testutil.FakeClock
import dev.pedalmate.testutil.FakeRideDao
import dev.pedalmate.testutil.FakeSampleDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RideRecorderTest {
    private val start = 1_700_000_000_000L
    private val clock = FakeClock(start)
    private val rides = FakeRideDao()
    private val samples = FakeSampleDao()
    private fun frame(p: Int? = 150, c: Int? = 80, r: Int? = 30, hr: Int? = 120, z: Int? = 3) = RideFrame(p, c, r, hr, z)

    private fun newRecorder(scope: kotlinx.coroutines.CoroutineScope) =
        RideRecorder(rides, samples, clock, scope)

    @Test fun `begin inserts an unfinished ride row`() = runTest {
        val rec = newRecorder(backgroundScope)
        val id = rec.begin("pz-43", 200)
        val row = rides.byId(id)!!
        assertEquals(start, row.startedAt); assertNull(row.endedAt)
        assertEquals("pz-43", row.workoutId); assertEquals(200, row.ftp)
    }

    @Test fun `60 second ride at 4 Hz gives 60 samples and a finished row`() = runTest {
        val rec = newRecorder(backgroundScope)
        val id = rec.begin(null, 200)
        for (i in 0 until 240) { clock.nowMs = start + i * 250L; rec.offer(frame(p = 100 + i % 50)) }
        clock.nowMs = start + 60_000
        val agg = rec.finish()!!
        assertEquals(60, samples.count(id))
        assertEquals((0..59).toList(), samples.forRide(id).map { it.tSec })
        val row = rides.byId(id)!!
        assertEquals(start + 60_000, row.endedAt); assertEquals(60, row.sampleCount)
        assertEquals(agg.avgPowerWatts, row.avgPowerWatts); assertNotNull(row.avgPowerWatts)
    }

    @Test fun `flush happens every 5 s in batches`() = runTest {
        val rec = newRecorder(backgroundScope)
        rec.begin(null, null)
        for (s in 0..4) { clock.nowMs = start + s * 1_000L; rec.offer(frame()) }
        advanceTimeBy(4_999); runCurrent()
        assertEquals(0, samples.rows.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(5, samples.rows.size)
        for (s in 5..7) { clock.nowMs = start + s * 1_000L; rec.offer(frame()) }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(5, 3), samples.batchSizes)
    }

    @Test fun `finish flushes the tail writes aggregates and stops the flush loop`() = runTest {
        val rec = newRecorder(backgroundScope)
        val id = rec.begin(null, 200)
        for (s in 0..9) { clock.nowMs = start + s * 1_000L; rec.offer(frame(p = s * 10)) }   // 0..90
        clock.nowMs = start + 10_500
        rec.finish()
        val row = rides.byId(id)!!
        assertEquals(10, samples.count(id))
        assertEquals(start + 10_500, row.endedAt)
        assertEquals(45, row.avgPowerWatts); assertEquals(90, row.maxPowerWatts)
        val batches = samples.batchSizes.size
        advanceTimeBy(60_000); runCurrent()
        assertEquals(batches, samples.batchSizes.size)           // loop cancelled
    }

    @Test fun `dropout frames store nulls and stay out of the averages`() = runTest {
        val rec = newRecorder(backgroundScope)
        val id = rec.begin(null, 200)
        clock.nowMs = start; rec.offer(frame(p = 100))
        clock.nowMs = start + 1_000; rec.offer(frame(p = null, c = null, r = null, z = null))
        clock.nowMs = start + 2_000; rec.offer(frame(p = 200))
        rec.finish()
        val rows = samples.forRide(id)
        assertNull(rows[1].powerWatts); assertNull(rows[1].cadenceRpm); assertNull(rows[1].zone)
        val ride = rides.byId(id)!!
        assertEquals(3, ride.sampleCount); assertEquals(150, ride.avgPowerWatts)
    }

    @Test fun `a failed flush keeps the batch and retries without duplicates`() = runTest {
        val rec = newRecorder(backgroundScope)
        val id = rec.begin(null, null)
        for (s in 0..2) { clock.nowMs = start + s * 1_000L; rec.offer(frame()) }
        samples.failNextInsert = true
        advanceTimeBy(5_000); runCurrent()
        assertEquals(0, samples.rows.size)                        // failed, nothing escaped
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(0, 1, 2), samples.forRide(id).map { it.tSec })
    }

    @Test fun `offer before begin and after finish is ignored`() = runTest {
        val rec = newRecorder(backgroundScope)
        rec.offer(frame())                                         // no ride: no crash
        val id = rec.begin(null, null)
        rec.offer(frame()); rec.finish()
        clock.nowMs = start + 5_000; rec.offer(frame())
        assertEquals(1, samples.count(id))
    }

    @Test fun `clock going backwards records nothing`() = runTest {
        val rec = newRecorder(backgroundScope)
        val id = rec.begin(null, null)
        clock.nowMs = start - 5_000; rec.offer(frame())
        rec.finish()
        assertEquals(0, samples.count(id))
    }

    @Test fun `second frame in the same second does not replace the first`() = runTest {
        val rec = newRecorder(backgroundScope)
        val id = rec.begin(null, null)
        clock.nowMs = start; rec.offer(frame(p = 111))
        clock.nowMs = start + 900; rec.offer(frame(p = 999))
        rec.finish()
        assertEquals(listOf(111), samples.forRide(id).map { it.powerWatts })
    }

    @Test fun `begin while a ride is active throws`() = runTest {
        val rec = newRecorder(backgroundScope)
        rec.begin(null, null)
        try { rec.begin(null, null); fail("expected IllegalStateException") } catch (_: IllegalStateException) { }
    }
}

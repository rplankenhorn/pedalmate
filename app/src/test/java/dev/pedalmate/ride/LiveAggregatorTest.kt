package dev.pedalmate.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveAggregatorTest {
    private fun f(p: Int? = null, c: Int? = null, r: Int? = null, hr: Int? = null) = RideFrame(p, c, r, hr, null)

    @Test fun `empty snapshot`() {
        assertEquals(LiveAggregates(0, null, null, null, null, null), LiveAggregator().snapshot())
    }

    @Test fun `single frame`() {
        val a = LiveAggregator().apply { add(f(200, 80, 30, 120)) }
        assertEquals(LiveAggregates(1, 200, 200, 80, 30, 120), a.snapshot())
    }

    @Test fun `average rounds half up`() {
        val a = LiveAggregator().apply { add(f(100)); add(f(101)) }
        assertEquals(101, a.snapshot().avgPowerWatts); assertEquals(101, a.snapshot().maxPowerWatts)
        val b = LiveAggregator().apply { add(f(100)); add(f(200)); add(f(301)) }
        assertEquals(200, b.snapshot().avgPowerWatts); assertEquals(301, b.snapshot().maxPowerWatts)
    }

    @Test fun `nulls are skipped not counted as zero`() {
        val a = LiveAggregator().apply { add(f(100, null)); add(f(null, null)); add(f(200, 60)) }
        val s = a.snapshot()
        assertEquals(3, s.sampleCount); assertEquals(150, s.avgPowerWatts)
        assertEquals(200, s.maxPowerWatts); assertEquals(60, s.avgCadenceRpm)
    }

    @Test fun `all-null frame still counts`() {
        val s = LiveAggregator().apply { add(f()) }.snapshot()
        assertEquals(LiveAggregates(1, null, null, null, null, null), s)
    }

    @Test fun `heart rate is averaged independently`() {
        val s = LiveAggregator().apply { add(f(hr = 100)); add(f(p = 50)) }.snapshot()
        assertEquals(100, s.avgHeartRateBpm); assertEquals(50, s.avgPowerWatts)
    }

    @Test fun `reset returns to empty`() {
        val a = LiveAggregator().apply { add(f(100, 1, 2, 3)); reset() }
        assertEquals(LiveAggregates(0, null, null, null, null, null), a.snapshot())
    }
}

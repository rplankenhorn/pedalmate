package dev.pedalmate.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetStatusTrackerTest {
    private val table = ZoneTable.forFtp(200)!!
    private val z3 = table.rangeOf(PowerZone.Z3)   // 150..179
    private val z4 = table.rangeOf(PowerZone.Z4)   // 180..209
    private val z7 = table.rangeOf(PowerZone.Z7)   // 300..null
    private val ftp1z2 = ZoneTable.forFtp(1)!!.rangeOf(PowerZone.Z2)   // empty

    @Test fun `tolerance of 3 W on both edges`() {
        assertEquals(TargetStatus.IN, TargetStatusTracker().update(0, 147, z3))
        assertEquals(TargetStatus.BELOW, TargetStatusTracker().update(0, 146, z3))
        assertEquals(TargetStatus.IN, TargetStatusTracker().update(0, 182, z3))
        assertEquals(TargetStatus.ABOVE, TargetStatusTracker().update(0, 183, z3))
    }

    @Test fun `a change is shown only after the debounce`() {
        val t = TargetStatusTracker()
        assertEquals(TargetStatus.IN, t.update(0, 160, z3))
        assertEquals(TargetStatus.IN, t.update(1_000, 140, z3))
        assertEquals(TargetStatus.IN, t.update(2_999, 140, z3))
        assertEquals(TargetStatus.BELOW, t.update(3_000, 140, z3))
    }

    @Test fun `flicker restarts the debounce timer`() {
        val t = TargetStatusTracker()
        assertEquals(TargetStatus.IN, t.update(0, 160, z3))
        assertEquals(TargetStatus.IN, t.update(1_000, 140, z3))
        assertEquals(TargetStatus.IN, t.update(1_500, 160, z3))
        assertEquals(TargetStatus.IN, t.update(2_000, 140, z3))
        assertEquals(TargetStatus.IN, t.update(3_999, 140, z3))
        assertEquals(TargetStatus.BELOW, t.update(4_000, 140, z3))
    }

    @Test fun `a changed target range is evaluated immediately`() {
        val t = TargetStatusTracker()
        assertEquals(TargetStatus.IN, t.update(0, 160, z3))
        assertEquals(TargetStatus.BELOW, t.update(500, 160, z4))
    }

    @Test fun `null watts returns null and resets`() {
        val t = TargetStatusTracker()
        assertEquals(TargetStatus.BELOW, t.update(0, 140, z3))
        assertNull(t.update(100, null, z3))
        assertEquals(TargetStatus.IN, t.update(200, 160, z3))
    }

    @Test fun `null or empty range returns null`() {
        assertNull(TargetStatusTracker().update(0, 160, null))
        assertNull(TargetStatusTracker().update(0, 160, ftp1z2))
    }

    @Test fun `open ended Z7 is never above`() {
        assertEquals(TargetStatus.IN, TargetStatusTracker().update(0, 900, z7))
        assertEquals(TargetStatus.IN, TargetStatusTracker().update(0, 297, z7))
        assertEquals(TargetStatus.BELOW, TargetStatusTracker().update(0, 296, z7))
    }
}

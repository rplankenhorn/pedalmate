package dev.pedalmate.overlay

import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.ZoneRange
import dev.pedalmate.workout.ZoneTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFormatTest {
    private val t = ZoneTable.forFtp(250)!!

    @Test fun formatClockTable() {
        val cases = listOf(
            600000L to "10:00", 599999L to "10:00", 600001L to "10:01", 1L to "0:01", 0L to "0:00",
            -5L to "0:00", 3725000L to "62:05", 180000L to "3:00", 59001L to "1:00", 61000L to "1:01",
        )
        for ((ms, expected) in cases) assertEquals("formatClock($ms)", expected, formatClock(ms))
    }

    @Test fun formatRangeCases() {
        assertEquals("263\u2013299 W", formatRange(t.rangeOf(PowerZone.Z5)))
        assertEquals("Z7 \u2265 375 W", formatRange(t.rangeOf(PowerZone.Z7)))
        assertEquals("0\u2013137 W", formatRange(t.rangeOf(PowerZone.Z1)))
        assertEquals("--", formatRange(null))
        assertEquals("--", formatRange(ZoneRange(PowerZone.Z1, 5, 3)))
    }

    @Test fun enDashIsU2013() {
        assertTrue(formatRange(t.rangeOf(PowerZone.Z5)).contains('\u2013'))
        assertFalse(formatRange(t.rangeOf(PowerZone.Z5)).contains('-'))
    }
}

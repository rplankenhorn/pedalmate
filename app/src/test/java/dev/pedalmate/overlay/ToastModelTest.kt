package dev.pedalmate.overlay

import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.StepInfo
import dev.pedalmate.workout.WorkoutEvent
import dev.pedalmate.workout.ZoneTable
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToastModelTest {
    private val table = ZoneTable.forFtp(250)!!
    private fun step(i: Int, label: String, zone: PowerZone?, seconds: Int) =
        StepInfo(i, label, zone, zone?.let { table.rangeOf(it) }, seconds)

    private val z4 = step(4, "Zone 4", PowerZone.Z4, 300)

    private fun to(s: StepInfo) = ToastModel.from(WorkoutEvent.IntervalChanged(z4, s))

    @Test fun zoneFiveInterval() {
        val m = to(step(5, "Zone 5", PowerZone.Z5, 180))
        assertEquals(ToastModel("NEXT INTERVAL", "ZONE 5", "263–299 W", "3:00"), m)
    }

    @Test fun cooldown() {
        val m = to(step(7, "Cooldown", PowerZone.Z1, 300))
        assertEquals("COOLDOWN", m.title)
        assertEquals("0–137 W", m.rangeText)
        assertEquals("5:00", m.durationText)
    }

    @Test fun noFtpMeansNoRange() {
        val m = to(StepInfo(5, "Zone 5", PowerZone.Z5, null, 180))
        assertNull(m.rangeText)
    }

    @Test fun zoneLessStep() {
        val m = to(StepInfo(2, "FTP window", null, null, 1200))
        assertNull(m.rangeText)
        assertEquals("20:00", m.durationText)
    }

    @Test fun zoneSevenIsOpenEnded() {
        assertEquals("Z7 ≥ 375 W", to(step(6, "Zone 7", PowerZone.Z7, 30)).rangeText)
    }

    @Test fun titleIsLocaleSafe() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr"))
            assertEquals("INTRO", to(step(1, "intro", PowerZone.Z2, 60)).title)
        } finally {
            Locale.setDefault(saved)
        }
    }
}

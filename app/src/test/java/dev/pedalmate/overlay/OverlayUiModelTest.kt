package dev.pedalmate.overlay

import dev.pedalmate.ride.RideSnapshot
import dev.pedalmate.ride.RideStatus
import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.StepInfo
import dev.pedalmate.workout.TargetStatus
import dev.pedalmate.workout.WorkoutPhase
import dev.pedalmate.workout.WorkoutState
import dev.pedalmate.workout.ZoneTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayUiModelTest {
    private val base = RideSnapshot.idle(250).copy(
        status = RideStatus.RUNNING, bikeState = ConnectionState.Connected, hrState = ConnectionState.Connected,
    )
    private val table = ZoneTable.forFtp(250)!!
    private fun step(i: Int, label: String, zone: PowerZone?, seconds: Int) =
        StepInfo(i, label, zone, zone?.let { table.rangeOf(it) }, seconds)
    private fun ws(cur: StepInfo?, next: StepInfo?, remainingMs: Long, progress: Float) =
        WorkoutState(WorkoutPhase.RUNNING, cur?.index ?: 0, 4, 0, remainingMs, 0, 0, 0, progress, cur, next)

    private val z5 = step(1, "Zone 5", PowerZone.Z5, 180)
    private val z2 = step(2, "Zone 2", PowerZone.Z2, 300)

    private fun live() = base.copy(
        powerWatts = 280, smoothedPowerWatts = 281, currentZone = PowerZone.Z5,
        targetZone = PowerZone.Z5, targetRange = table.rangeOf(PowerZone.Z5), targetStatus = TargetStatus.IN,
        workout = ws(z5, z2, 125_000, 0.5f), heartRateBpm = 151, cadenceRpm = 92, resistancePercent = 41,
    )

    @Test fun liveWorkout() {
        val m = OverlayUiModel.from(live())
        assertEquals("Z5", m.zoneChip)
        assertEquals(PowerZone.Z5, m.zone)
        assertEquals("281", m.wattsText)
        assertNull(m.banner)
        assertEquals("263–299 W", m.targetText)
        assertEquals("IN ZONE", m.statusText)
        assertEquals(TargetStatus.IN, m.targetStatus)
        assertEquals("Zone 5", m.intervalName)
        assertEquals("2:05", m.intervalTime)
        assertEquals("Next: Zone 2 5:00", m.nextText)
        assertEquals("151", m.hrText)
        assertEquals("92", m.cadenceText)
        assertEquals("41%", m.resistanceText)
        assertEquals(0.5f, m.progress)
        assertEquals(false, m.needsFtp)
        assertEquals("Z5  281 W", m.pillText)
    }

    @Test fun statusTexts() {
        assertEquals("BELOW", OverlayUiModel.from(live().copy(targetStatus = TargetStatus.BELOW)).statusText)
        assertEquals("ABOVE", OverlayUiModel.from(live().copy(targetStatus = TargetStatus.ABOVE)).statusText)
        assertNull(OverlayUiModel.from(live().copy(targetStatus = null)).statusText)
    }

    @Test fun dropoutShowsNoSensorAndDashes() {
        for (state in listOf(ConnectionState.Disconnected, ConnectionState.Unavailable)) {
            val m = OverlayUiModel.from(
                live().copy(
                    bikeState = state, powerWatts = null, smoothedPowerWatts = null, cadenceRpm = null,
                    resistancePercent = null, currentZone = null, targetStatus = null,
                ),
            )
            assertEquals("NO SENSOR", m.banner)
            assertEquals("--", m.wattsText)
            assertEquals("--", m.zoneChip)
            assertEquals("--", m.cadenceText)
            assertEquals("--", m.resistanceText)
            assertNull(m.statusText)
            assertEquals("NO SENSOR", m.pillText)
            assertEquals("263–299 W", m.targetText)
            assertEquals("2:05", m.intervalTime)
        }
    }

    @Test fun pillNeverShowsWattsWhenBikeNotConnected() {
        val m = OverlayUiModel.from(live().copy(bikeState = ConnectionState.Disconnected, smoothedPowerWatts = 183))
        assertEquals("NO SENSOR", m.pillText)
    }

    @Test fun ftpNull() {
        val m = OverlayUiModel.from(
            live().copy(
                ftp = null, currentZone = null, targetRange = null, targetZone = PowerZone.Z2, smoothedPowerWatts = 183,
            ),
        )
        assertEquals("--", m.zoneChip)
        assertEquals("183", m.wattsText)
        assertEquals("Z2", m.targetText)
        assertTrue(m.needsFtp)
        assertEquals("--  183 W", m.pillText)
    }

    @Test fun connectedNoReading() {
        val m = OverlayUiModel.from(live().copy(smoothedPowerWatts = null, currentZone = null))
        assertEquals("--", m.wattsText)
        assertEquals("--  --", m.pillText)
    }

    @Test fun intervalNames() {
        val idle = OverlayUiModel.from(base.copy(status = RideStatus.IDLE, workout = null))
        assertEquals("Ready", idle.intervalName)
        assertNull(idle.intervalTime)
        assertNull(idle.nextText)
        assertEquals("Done", OverlayUiModel.from(base.copy(status = RideStatus.FINISHED)).intervalName)
        val free = OverlayUiModel.from(base.copy(workout = null, elapsedMs = 61_000))
        assertEquals("Free ride", free.intervalName)
        assertEquals("1:01", free.intervalTime)
        assertNull(free.nextText)
        assertNull(free.targetText)
        assertEquals("Ready", OverlayUiModel.from(base.copy(workout = ws(null, null, 0, 0f))).intervalName)
    }

    @Test fun lastInterval() {
        assertEquals("Last interval", OverlayUiModel.from(live().copy(workout = ws(z5, null, 1000, 0.9f))).nextText)
    }

    @Test fun openEndedTarget() {
        val m = OverlayUiModel.from(live().copy(targetZone = PowerZone.Z7, targetRange = table.rangeOf(PowerZone.Z7)))
        assertEquals("Z7 ≥ 375 W", m.targetText)
    }

    @Test fun nullHrAndResistance() {
        val m = OverlayUiModel.from(live().copy(heartRateBpm = null, resistancePercent = null))
        assertEquals("--", m.hrText)
        assertEquals("--", m.resistanceText)
    }

    @Test fun idleNullFtpDoesNotThrow() {
        val m = OverlayUiModel.from(RideSnapshot.idle(null))
        assertTrue(m.needsFtp)
        assertEquals("--", m.zoneChip)
        assertEquals("--", m.wattsText)
    }
}

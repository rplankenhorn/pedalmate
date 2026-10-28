package dev.pedalmate.audio

import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.StepInfo
import dev.pedalmate.workout.WorkoutEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CuePolicyTest {
    private fun step(index: Int, zone: PowerZone?) = StepInfo(index, "s$index", zone, null, 60)

    private fun change(from: PowerZone?, to: PowerZone?) =
        CuePolicy.cueFor(WorkoutEvent.IntervalChanged(step(0, from), step(1, to)))

    @Test fun startedMapsToStart() {
        assertEquals(Cue.START, CuePolicy.cueFor(WorkoutEvent.Started(step(0, PowerZone.Z2))))
    }

    @Test fun countdownMapsToCountdown() {
        for (n in 3 downTo 1) assertEquals(Cue.COUNTDOWN, CuePolicy.cueFor(WorkoutEvent.Countdown(n)))
    }

    @Test fun higherZoneIsHarder() = assertEquals(Cue.STEP_HARDER, change(PowerZone.Z3, PowerZone.Z4))
    @Test fun lowerZoneIsEasier() = assertEquals(Cue.STEP_EASIER, change(PowerZone.Z4, PowerZone.Z3))
    @Test fun sameZoneIsSame() = assertEquals(Cue.STEP_SAME, change(PowerZone.Z3, PowerZone.Z3))
    @Test fun nullToZoneIsSame() = assertEquals(Cue.STEP_SAME, change(null, PowerZone.Z3))
    @Test fun zoneToNullIsSame() = assertEquals(Cue.STEP_SAME, change(PowerZone.Z3, null))
    @Test fun hrTargetStepIsSame() {
        val z2 = StepInfo(0, "z", PowerZone.Z2, null, 60)
        val hr = StepInfo(1, "hr", null, null, 60, hrTarget = true)
        val res = StepInfo(2, "res", null, null, 60, resistance = 25)
        assertEquals(Cue.STEP_SAME, CuePolicy.cueFor(WorkoutEvent.IntervalChanged(z2, hr)))
        assertEquals(Cue.STEP_SAME, CuePolicy.cueFor(WorkoutEvent.IntervalChanged(hr, res)))
    }

    @Test fun nullToNullIsSame() = assertEquals(Cue.STEP_SAME, change(null, null))
    @Test fun fullSpanIsHarder() = assertEquals(Cue.STEP_HARDER, change(PowerZone.Z1, PowerZone.Z7))

    @Test fun skippedIsSilent() = assertNull(CuePolicy.cueFor(WorkoutEvent.Skipped(0)))
    @Test fun finishedMapsToFinish() = assertEquals(Cue.FINISH, CuePolicy.cueFor(WorkoutEvent.Finished))
}

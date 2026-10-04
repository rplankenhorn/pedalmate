package dev.pedalmate.audio

import dev.pedalmate.workout.WorkoutEvent

/** Pure mapping from workout events to cues. */
object CuePolicy {
    /** Returns the cue for [event], or null when silent. Uses only the event payload. A null zone is neutral. */
    fun cueFor(event: WorkoutEvent): Cue? = when (event) {
        is WorkoutEvent.Started -> Cue.START
        is WorkoutEvent.Countdown -> Cue.COUNTDOWN
        is WorkoutEvent.IntervalChanged -> {
            val from = event.from.zone
            val to = event.to.zone
            when {
                from == null || to == null -> Cue.STEP_SAME
                to.number > from.number -> Cue.STEP_HARDER
                to.number < from.number -> Cue.STEP_EASIER
                else -> Cue.STEP_SAME
            }
        }
        is WorkoutEvent.Skipped -> null
        WorkoutEvent.Finished -> Cue.FINISH
    }
}

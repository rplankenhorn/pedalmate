package dev.pedalmate.overlay

import dev.pedalmate.workout.WorkoutEvent
import java.util.Locale

/** Text shown by the interval toast; [rangeText] is null when the new step has no watt range. */
data class ToastModel(val heading: String, val title: String, val rangeText: String?, val durationText: String) {
    companion object {
        fun from(e: WorkoutEvent.IntervalChanged): ToastModel = ToastModel(
            heading = "NEXT INTERVAL",
            title = e.to.label.uppercase(Locale.ROOT),
            rangeText = e.to.wattRange?.let { formatRange(it) },
            durationText = formatClock(e.to.seconds * 1000L),
        )
    }
}

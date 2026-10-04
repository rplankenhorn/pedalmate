package dev.pedalmate.overlay

import dev.pedalmate.workout.ZoneRange

/** Formats [ms] as `m:ss`, rounding seconds up; minutes are not padded or wrapped, negatives give `0:00`. */
fun formatClock(ms: Long): String {
    val totalSeconds = if (ms <= 0L) 0L else (ms + 999L) / 1000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** Formats a watt range for display; null and degenerate rows give `--`. */
fun formatRange(range: ZoneRange?): String = when {
    range == null || range.isEmpty -> "--"
    range.highWatts == null -> "Z${range.zone.number} ≥ ${range.lowWatts} W"
    else -> "${range.lowWatts}–${range.highWatts} W"
}

package dev.pedalmate.workout

enum class TargetStatus { BELOW, IN, ABOVE }

/** Below/in/above the target range with +-[toleranceWatts] slack; a change is shown only after [debounceMs] of agreement. */
class TargetStatusTracker(private val debounceMs: Long = 2_000L, private val toleranceWatts: Int = 3) {
    private var range: ZoneRange? = null
    private var shown: TargetStatus? = null
    private var pending: TargetStatus? = null
    private var pendingSince = 0L

    /** Null when there is no power reading or no usable (non-empty) target range. */
    fun update(nowMs: Long, watts: Int?, range: ZoneRange?): TargetStatus? {
        if (watts == null || range == null || range.isEmpty) { reset(); return null }
        if (range != this.range) { this.range = range; shown = null; pending = null }   // new target: judge immediately
        val raw = when {
            watts < range.lowWatts - toleranceWatts -> TargetStatus.BELOW
            range.highWatts != null && watts > range.highWatts + toleranceWatts -> TargetStatus.ABOVE
            else -> TargetStatus.IN
        }
        val current = shown
        if (current == null) { shown = raw; return raw }
        if (raw == current) { pending = null; return current }
        if (pending != raw) { pending = raw; pendingSince = nowMs }
        if (nowMs - pendingSince >= debounceMs) { shown = raw; pending = null }
        return shown
    }

    fun reset() { range = null; shown = null; pending = null }
}

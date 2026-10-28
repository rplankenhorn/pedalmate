package dev.pedalmate.workout

enum class TargetStatus { BELOW, IN, ABOVE }

/** Below/in/above the target range with +-[toleranceWatts] slack; a change is shown only after [debounceMs] of agreement. */
class TargetStatusTracker(private val debounceMs: Long = 2_000L, private val toleranceWatts: Int = 3) {
    private var key: Triple<Int?, Int?, Int>? = null
    private var shown: TargetStatus? = null
    private var pending: TargetStatus? = null
    private var pendingSince = 0L

    /** Null when there is no power reading or no usable (non-empty) target range. */
    fun update(nowMs: Long, watts: Int?, range: ZoneRange?): TargetStatus? {
        if (watts == null || range == null || range.isEmpty) { reset(); return null }
        return judge(nowMs, watts, range.lowWatts, range.highWatts, toleranceWatts)
    }

    /** Same debounce against an integer band (e.g. heart rate) with zero tolerance; null when value or band is missing or empty. */
    fun updateBand(nowMs: Long, value: Int?, band: IntRange?): TargetStatus? {
        if (value == null || band == null || band.isEmpty()) { reset(); return null }
        return judge(nowMs, value, band.first, band.last, 0)
    }

    private fun judge(nowMs: Long, value: Int, low: Int, high: Int?, tolerance: Int): TargetStatus {
        val k = Triple<Int?, Int?, Int>(low, high, tolerance)
        if (k != key) { key = k; shown = null; pending = null }   // new target: judge immediately
        val raw = when {
            value < low - tolerance -> TargetStatus.BELOW
            high != null && value > high + tolerance -> TargetStatus.ABOVE
            else -> TargetStatus.IN
        }
        val current = shown
        if (current == null) { shown = raw; return raw }
        if (raw == current) { pending = null; return current }
        if (pending != raw) { pending = raw; pendingSince = nowMs }
        if (nowMs - pendingSince >= debounceMs) { shown = raw; pending = null }
        return shown!!
    }

    fun reset() { key = null; shown = null; pending = null }
}

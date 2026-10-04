package dev.pedalmate.workout

/** Mean power over the trailing [windowMs]; a sample at exactly `now - windowMs` is already expired. */
class PowerSmoother(private val windowMs: Long = 3_000L) {
    private val samples = ArrayDeque<Pair<Long, Int>>()

    fun add(nowMs: Long, watts: Int) {
        samples.addLast(nowMs to watts)
        prune(nowMs)
    }

    /** Mean of the samples in the window, rounded half up, or null when the window is empty. */
    fun value(nowMs: Long): Int? {
        prune(nowMs)
        if (samples.isEmpty()) return null
        val n = samples.size
        val sum = samples.sumOf { it.second.toLong() }
        return ((sum + n / 2) / n).toInt()
    }

    fun reset() = samples.clear()

    private fun prune(nowMs: Long) {
        while (samples.isNotEmpty() && samples.first().first <= nowMs - windowMs) samples.removeFirst()
    }
}

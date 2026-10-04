package dev.pedalmate.workout

/**
 * A watt range for one [zone]. [highWatts] is inclusive and null for the open-ended Z7.
 */
data class ZoneRange(val zone: PowerZone, val lowWatts: Int, val highWatts: Int?) {
    /** True for the degenerate rows tiny FTPs produce; such a row contains no watt value. */
    val isEmpty: Boolean get() = highWatts != null && highWatts < lowWatts
}

/**
 * Power zones for one FTP, using integer-percent edges: `bound_i = ceil(pct_i * ftp / 100)` in integer math.
 * Zone i covers `[bound_{i-1}, bound_i - 1]`; Z1 starts at 0 and Z7 is open-ended.
 */
class ZoneTable private constructor(val ftp: Int) {
    private val bounds: IntArray = IntArray(6) { i -> (PowerZone.entries[i].upperPercent!! * ftp + 99) / 100 }

    /** Z1..Z7 in order. */
    val ranges: List<ZoneRange> = PowerZone.entries.map { z ->
        val low = if (z.number == 1) 0 else bounds[z.number - 2]
        val high = if (z == PowerZone.Z7) null else bounds[z.number - 1] - 1
        ZoneRange(z, low, high)
    }

    fun rangeOf(zone: PowerZone): ZoneRange = ranges[zone.number - 1]

    /** The zone containing [watts]; negative watts count as 0. */
    fun zoneFor(watts: Int): PowerZone {
        val w = watts.coerceAtLeast(0)
        for (i in 0..5) if (w < bounds[i]) return PowerZone.entries[i]
        return PowerZone.Z7
    }

    companion object {
        private const val MAX_FTP = 1_000_000

        /** Null when [ftp] is null, not positive, or above one million (no table, never a crash). */
        fun forFtp(ftp: Int?): ZoneTable? = if (ftp == null || ftp <= 0 || ftp > MAX_FTP) null else ZoneTable(ftp)
    }
}

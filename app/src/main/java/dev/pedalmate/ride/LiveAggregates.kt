package dev.pedalmate.ride

/** Running ride summary. Averages are null when no non-null value was seen. */
data class LiveAggregates(
    val sampleCount: Int,
    val avgPowerWatts: Int?,
    val maxPowerWatts: Int?,
    val avgCadenceRpm: Int?,
    val avgResistancePercent: Int?,
    val avgHeartRateBpm: Int?,
)

/** One second of ride data; null means the sensor was not live, never a stale value. */
data class RideFrame(
    val powerWatts: Int?,
    val cadenceRpm: Int?,
    val resistancePercent: Int?,
    val heartRateBpm: Int?,
    val zone: Int?,
)

/** Accumulates [RideFrame]s into [LiveAggregates], averaging non-null values only (half up). */
class LiveAggregator {
    private var samples = 0
    private var powerSum = 0L
    private var powerN = 0
    private var maxPower: Int? = null
    private var cadenceSum = 0L
    private var cadenceN = 0
    private var resistanceSum = 0L
    private var resistanceN = 0
    private var hrSum = 0L
    private var hrN = 0

    fun add(frame: RideFrame) {
        samples++
        frame.powerWatts?.let { powerSum += it; powerN++; maxPower = maxOf(maxPower ?: it, it) }
        frame.cadenceRpm?.let { cadenceSum += it; cadenceN++ }
        frame.resistancePercent?.let { resistanceSum += it; resistanceN++ }
        frame.heartRateBpm?.let { hrSum += it; hrN++ }
    }

    fun snapshot() = LiveAggregates(
        sampleCount = samples,
        avgPowerWatts = avg(powerSum, powerN),
        maxPowerWatts = maxPower,
        avgCadenceRpm = avg(cadenceSum, cadenceN),
        avgResistancePercent = avg(resistanceSum, resistanceN),
        avgHeartRateBpm = avg(hrSum, hrN),
    )

    fun reset() {
        samples = 0; powerSum = 0; powerN = 0; maxPower = null
        cadenceSum = 0; cadenceN = 0; resistanceSum = 0; resistanceN = 0; hrSum = 0; hrN = 0
    }

    private fun avg(sum: Long, n: Int): Int? = if (n == 0) null else ((sum + n / 2) / n).toInt()
}

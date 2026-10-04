package dev.pedalmate.sensor

import kotlin.math.roundToInt
import kotlin.random.Random

/** Deterministic scripted ride for emulator work: Z1..Z7 ramp with noise and a periodic dropout. */
class ScriptedProfile(
    private val seed: Long = 1L,
    private val ftp: Int = 200,
    private val dropouts: Boolean = true,
) {
    private val cycleSec = if (dropouts) CYCLE_WITH_DROPOUT_SEC else RAMP_SEC

    /** Reading for second [tSec] of the script, or null while the sensor is "dropped out". */
    fun sample(tSec: Int): BikeMetrics? {
        require(tSec >= 0) { "tSec must be >= 0" }
        val t = tSec % cycleSec
        if (t >= RAMP_SEC) return null
        val z = t / ZONE_BLOCK_SEC
        val rnd = Random(seed * 1_000_003L + tSec)
        val watts = (ftp * FRACTION[z]).roundToInt() + rnd.nextInt(-5, 6)
        val cadence = 60 + 4 * z + rnd.nextInt(-2, 3)
        val resistance = 20 + 8 * z + rnd.nextInt(-2, 3)
        return BikeMetrics(
            cadenceRpm = cadence.coerceAtLeast(0),
            resistancePercent = resistance.coerceIn(0, 100),
            powerWatts = watts.coerceAtLeast(0),
        )
    }

    private companion object {
        val FRACTION = doubleArrayOf(0.40, 0.65, 0.82, 0.97, 1.12, 1.35, 1.70)
        const val ZONE_BLOCK_SEC = 20
        const val RAMP_SEC = 140
        const val CYCLE_WITH_DROPOUT_SEC = 146
    }
}

// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/sensor/BikeMetrics.kt. Modified by PedalMate.
package dev.pedalmate.sensor

/**
 * A single snapshot of live bike sensor readings.
 *
 * @param cadenceRpm pedaling cadence in revolutions per minute
 * @param resistancePercent resistance knob position, 0-100
 * @param powerWatts instantaneous output power in watts
 */
data class BikeMetrics(
    val cadenceRpm: Int,
    val resistancePercent: Int,
    val powerWatts: Int,
) {
    companion object {
        val ZERO = BikeMetrics(cadenceRpm = 0, resistancePercent = 0, powerWatts = 0)
    }
}

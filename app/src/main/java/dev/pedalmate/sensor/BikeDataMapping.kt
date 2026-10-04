// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/sensor/BikeDataMapping.kt. Modified by PedalMate.
package dev.pedalmate.sensor

import com.onepeloton.affernetservice.BikeData

/**
 * Raw affernet sensor frame -> [BikeMetrics], as delivered by the `IBikeInterface` binder.
 *
 * Scaling:
 *
 * - `cadenceRpm`        = `rpm`                (raw long, already RPM)
 * - `powerWatts`        = `power / 100`        (raw is centi-watts)
 * - `resistancePercent` = `currentResistance`  (raw int, already 0..100)
 *
 * Values are clamped defensively so a garbage frame can never produce out-of-range readings.
 */
internal fun BikeData.toBikeMetrics(): BikeMetrics = BikeMetrics(
    cadenceRpm = rpm.coerceIn(0L, MAX_CADENCE).toInt(),
    resistancePercent = currentResistance.coerceIn(0, 100),
    powerWatts = (power / POWER_SCALE).coerceIn(0L, MAX_WATTS).toInt(),
)

/** Raw power is centi-watts (watts x 100). */
private const val POWER_SCALE = 100L
/** Defensive sanity caps; no real reading comes near these. */
private const val MAX_CADENCE = 999L
private const val MAX_WATTS = 9_999L

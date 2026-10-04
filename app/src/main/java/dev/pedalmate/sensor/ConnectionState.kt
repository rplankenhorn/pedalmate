// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/sensor/ConnectionState.kt. Modified by PedalMate.
package dev.pedalmate.sensor

/**
 * Connectivity state of a [BikeDataSource].
 *
 * UI should treat anything other than [Connected] as "sensors not detected" (PRD P0-9) —
 * never silently show stale/zero metrics as if they were live readings.
 */
sealed interface ConnectionState {
    /** Sensor is bound and actively reporting metrics. */
    data object Connected : ConnectionState

    /** Sensor was connected but the link has dropped (e.g. transient signal loss). */
    data object Disconnected : ConnectionState

    /** Sensor has never been reachable (e.g. system service missing/bind failure). */
    data object Unavailable : ConnectionState
}

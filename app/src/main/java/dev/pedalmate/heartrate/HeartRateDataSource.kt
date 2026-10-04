// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/heartrate/HeartRateDataSource.kt. Modified by PedalMate.
package dev.pedalmate.heartrate

import dev.pedalmate.sensor.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over a single paired BLE heart-rate strap's live feed (PRD P1-4, T17). Mirrors
 * [dev.pedalmate.sensor.BikeDataSource]'s shape deliberately — same
 * "narrow interface + real/fake implementations" pattern, reusing the existing
 * [ConnectionState] sealed type rather than inventing a parallel one.
 */
interface HeartRateDataSource {
    /** Latest heart rate in bpm, or `null` before any reading has arrived. */
    val bpm: StateFlow<Int?>

    /** Whether the strap's connection is currently live. */
    val connectionState: StateFlow<ConnectionState>
}

/**
 * A [HeartRateDataSource] that also owns a connect/disconnect lifecycle — separated from the
 * plain read-only interface so [HeartRateConnector] can test its reconnect-on-profile-switch
 * logic against a lightweight fake without that fake needing to be a real
 * [BleHeartRateDataSource].
 */
interface ManagedHeartRateDataSource : HeartRateDataSource {
    /** Begins connecting. Implementations must never throw (see [BleHeartRateDataSource]'s doc). */
    fun start()

    /** Disconnects and releases any held resources. Safe to call even if [start] was never called. */
    fun stop()
}

/**
 * A [ManagedHeartRateDataSource] that counts the notification frames it has decoded. [HeartRateConnector]
 * judges liveness from this counter: a steady heart rate repeats the same value, and a StateFlow never
 * re-emits an equal value, so bpm changes cannot tell "steady" from "strap dead".
 */
interface HeartRateLink : ManagedHeartRateDataSource {
    /** Number of Heart Rate Measurement frames decoded since [start]. */
    val framesReceived: Long
}

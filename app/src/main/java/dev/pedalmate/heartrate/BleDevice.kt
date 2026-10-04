// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/heartrate/BleDevice.kt. Modified by PedalMate.
package dev.pedalmate.heartrate

/**
 * A BLE device found while scanning for heart-rate straps (PRD P1-4, T17).
 *
 * @param address the device's BLE MAC address — what gets persisted as
 *   [SavedHrDevice.address] once paired
 * @param name advertised device name, if any (some straps advertise with no name at all)
 */
data class BleDevice(
    val address: String,
    val name: String?,
)

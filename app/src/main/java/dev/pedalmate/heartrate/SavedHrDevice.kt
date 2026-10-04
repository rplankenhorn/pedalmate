package dev.pedalmate.heartrate

/** The paired heart-rate device: last known [address] (may rotate for phone relays) and advertised [name]. */
data class SavedHrDevice(val address: String, val name: String?)

/** Synchronous persistence for the paired device; A10 provides the DataStore-backed adapter. */
interface SavedHrDeviceStore {
    fun load(): SavedHrDevice?
    fun save(device: SavedHrDevice?)
}

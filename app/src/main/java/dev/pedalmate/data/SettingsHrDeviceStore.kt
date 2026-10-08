package dev.pedalmate.data

import dev.pedalmate.heartrate.SavedHrDevice
import dev.pedalmate.heartrate.SavedHrDeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Synchronous [SavedHrDeviceStore] view over [SettingsStore]: reads a cached copy, writes through on IO. */
class SettingsHrDeviceStore(private val settings: SettingsStore, private val scope: CoroutineScope) : SavedHrDeviceStore {
    private val lock = Any()
    private var cached: SavedHrDevice? = null

    // Set by save() until the settings flow reports that value back; flow emissions that differ are stale.
    private var hasPending = false
    private var pending: SavedHrDevice? = null

    init {
        scope.launch(Dispatchers.IO) {
            settings.settings.collect {
                val stored = it.hrAddress?.let { a -> SavedHrDevice(a, it.hrName) }
                synchronized(lock) {
                    if (!hasPending || stored == pending) {
                        hasPending = false
                        cached = stored
                    }
                }
            }
        }
    }

    override fun load(): SavedHrDevice? = synchronized(lock) { cached }

    override fun save(device: SavedHrDevice?) {
        synchronized(lock) {
            cached = device
            pending = device
            hasPending = true
        }
        scope.launch(Dispatchers.IO) { settings.setHrDevice(device?.address, device?.name) }
    }
}

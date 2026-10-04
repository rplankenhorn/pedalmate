package dev.pedalmate.data

import dev.pedalmate.heartrate.SavedHrDevice
import dev.pedalmate.heartrate.SavedHrDeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Synchronous [SavedHrDeviceStore] view over [SettingsStore]: reads a cached copy, writes through on IO. */
class SettingsHrDeviceStore(private val settings: SettingsStore, private val scope: CoroutineScope) : SavedHrDeviceStore {
    @Volatile private var cached: SavedHrDevice? = null

    init {
        scope.launch(Dispatchers.IO) {
            settings.settings.collect { cached = it.hrAddress?.let { a -> SavedHrDevice(a, it.hrName) } }
        }
    }

    override fun load(): SavedHrDevice? = cached

    override fun save(device: SavedHrDevice?) {
        cached = device
        scope.launch(Dispatchers.IO) { settings.setHrDevice(device?.address, device?.name) }
    }
}

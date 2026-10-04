package dev.pedalmate.heartrate

/**
 * Applies a rider's pairing choice. A real [HeartRateConnector] saves and reconnects immediately;
 * any other source (the mock flavor) only needs the device saved.
 */
class HrPairing(private val hr: ManagedHeartRateDataSource, private val store: SavedHrDeviceStore) {
    fun use(device: BleDevice) {
        if (hr is HeartRateConnector) hr.useDevice(device) else store.save(SavedHrDevice(device.address, device.name))
    }

    fun forget() {
        if (hr is HeartRateConnector) hr.forget() else store.save(null)
    }
}

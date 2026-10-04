package dev.pedalmate.testutil

import dev.pedalmate.heartrate.BleDevice
import dev.pedalmate.heartrate.BleScanner
import dev.pedalmate.heartrate.HeartRateLink
import dev.pedalmate.heartrate.SavedHrDevice
import dev.pedalmate.heartrate.SavedHrDeviceStore
import dev.pedalmate.sensor.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeScanner : BleScanner {
    var available = true
    var failOnStart: String? = null
    var startCount = 0; private set
    var stopCount = 0; private set
    var scanning = false; private set
    private var onFound: ((BleDevice) -> Unit)? = null
    private var onFailed: ((String) -> Unit)? = null

    override fun isBluetoothAvailable() = available
    override fun startScan(onDeviceFound: (BleDevice) -> Unit, onScanFailed: (String) -> Unit) {
        startCount++
        val failure = failOnStart
        if (failure != null) { onScanFailed(failure); return }
        scanning = true; onFound = onDeviceFound; onFailed = onScanFailed
    }
    override fun stopScan() { stopCount++; scanning = false; onFound = null; onFailed = null }
    fun emit(device: BleDevice) { onFound?.invoke(device) }
    fun fail(reason: String) { onFailed?.invoke(reason) }
}

class FakeLink(val address: String, val createdAtMs: Long) : HeartRateLink {
    private val _bpm = MutableStateFlow<Int?>(null)
    override val bpm: StateFlow<Int?> = _bpm
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _state
    override var framesReceived = 0L; private set
    var started = false; private set
    var stopped = false; private set

    override fun start() { started = true }
    override fun stop() { stopped = true }
    /** One notification frame; equal consecutive values still count as frames. */
    fun frame(bpm: Int) { _bpm.value = bpm; framesReceived++; _state.value = ConnectionState.Connected }
    fun drop() { _state.value = ConnectionState.Disconnected }
}

class FakeLinkFactory(private val clock: () -> Long) : (String) -> HeartRateLink {
    val created = mutableListOf<FakeLink>()
    override fun invoke(address: String): HeartRateLink = FakeLink(address, clock()).also { created += it }
}

class MemStore(var device: SavedHrDevice? = null) : SavedHrDeviceStore {
    var saves = 0; private set
    override fun load() = device
    override fun save(device: SavedHrDevice?) { saves++; this.device = device }
}

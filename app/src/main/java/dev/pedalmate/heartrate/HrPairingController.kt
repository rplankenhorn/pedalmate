package dev.pedalmate.heartrate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the heart-rate pairing panel shows. */
sealed interface PairingState {
    data object Idle : PairingState
    data class Scanning(val devices: List<BleDevice>) : PairingState
    data class Done(val devices: List<BleDevice>) : PairingState
    data class Failed(val reason: String) : PairingState
    data class Unavailable(val reason: String) : PairingState
    data class Paired(val device: BleDevice) : PairingState
}

/** Drives a time-boxed BLE scan for heart-rate straps and applies the rider's choice through [HrPairing]. */
class HrPairingController(
    private val scanner: BleScanner,
    private val pairing: HrPairing,
    private val scope: CoroutineScope,
    private val scanWindowMs: Long = 10_000L,
) {
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private val found = LinkedHashMap<String, BleDevice>()
    private var generation = 0
    private var timer: Job? = null

    fun startScan() {
        val gen: Int
        synchronized(this) {
            if (_state.value is PairingState.Scanning) return
            if (!scanner.isBluetoothAvailable()) {
                _state.value = PairingState.Unavailable("Bluetooth is off or missing")
                return
            }
            found.clear()
            gen = ++generation
            _state.value = PairingState.Scanning(emptyList())
            timer = scope.launch {
                delay(scanWindowMs)
                finishScan(gen)
            }
        }
        scanner.startScan(
            onDeviceFound = { onFound(gen, it) },
            onScanFailed = { onFailed(gen, it) },
        )
    }

    fun stopScan() {
        synchronized(this) {
            val wasScanning = _state.value is PairingState.Scanning
            generation++
            timer?.cancel()
            _state.value = PairingState.Idle
            if (!wasScanning) return
        }
        scanner.stopScan()
    }

    fun select(device: BleDevice) {
        val wasScanning: Boolean
        synchronized(this) {
            wasScanning = _state.value is PairingState.Scanning
            generation++
            timer?.cancel()
        }
        if (wasScanning) scanner.stopScan()
        pairing.use(device)
        _state.value = PairingState.Paired(device)
    }

    fun forget() {
        pairing.forget()
        _state.value = PairingState.Idle
    }

    private fun onFound(gen: Int, device: BleDevice) = synchronized(this) {
        if (gen != generation || _state.value !is PairingState.Scanning) return
        val existing = found[device.address]
        found[device.address] = if (existing != null && device.name == null) existing else device
        _state.value = PairingState.Scanning(sorted())
    }

    private fun onFailed(gen: Int, reason: String) {
        synchronized(this) {
            if (gen != generation) return
            generation++
            timer?.cancel()
            _state.value = PairingState.Failed(reason)
        }
        scanner.stopScan()
    }

    private fun finishScan(gen: Int) {
        synchronized(this) {
            if (gen != generation) return
            generation++
            _state.value = PairingState.Done(sorted())
        }
        scanner.stopScan()
    }

    private fun sorted(): List<BleDevice> =
        found.values.sortedWith(compareBy<BleDevice, String?>(nullsLast()) { it.name?.lowercase() }.thenBy { it.address })
}

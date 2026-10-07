package dev.pedalmate.heartrate

import android.util.Log
import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.sensor.TickScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps a heart-rate strap (or iPhone relay) connected: saved address, then name rescan, then first device. */
class HeartRateConnector(
    private val scanner: BleScanner,
    private val linkFactory: (address: String) -> HeartRateLink,
    private val store: SavedHrDeviceStore,
    private val clock: () -> Long,
    private val scheduler: TickScheduler,
) : ManagedHeartRateDataSource {

    private enum class Phase { STOPPED, NO_DEVICE, CONNECTING, SCANNING, CONNECTED, WAITING }

    private val _bpm = MutableStateFlow<Int?>(null)
    override val bpm: StateFlow<Int?> = _bpm.asStateFlow()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private var phase = Phase.STOPPED
    private var link: HeartRateLink? = null
    private var attempt: SavedHrDevice? = null
    private var attemptViaScan = false
    private var attemptIsFallback = false
    private var attemptStartedAt = 0L
    private var scanActive = false
    private var scanStartedAt = 0L
    private val found = ArrayList<BleDevice>()
    private var scanFailure: String? = null
    private var failures = 0
    private var nextCycleAt = 0L
    private var everConnected = false
    private var lastFrames = 0L
    private var lastFrameAt = 0L

    @Synchronized override fun start() {
        if (phase != Phase.STOPPED) return
        failures = 0; everConnected = false
        publishLost()
        scheduler.start(TICK_MS) { onTick() }
        beginCycle(clock())
    }

    @Synchronized override fun stop() {
        if (phase == Phase.STOPPED) return
        scheduler.stop(); abortScan(); closeLink()
        phase = Phase.STOPPED
        _bpm.value = null; _state.value = ConnectionState.Unavailable
    }

    /** Pairing: remember [device] and connect to it now. */
    @Synchronized fun useDevice(device: BleDevice) {
        runCatching { store.save(SavedHrDevice(device.address, device.name)) }
            .onFailure { Log.w(TAG, "saving paired device failed", it) }
        if (phase == Phase.STOPPED) return
        abortScan(); closeLink()
        _bpm.value = null; publishLost()
        failures = 0
        beginCycle(clock())
    }

    /** Clears the saved device and disconnects. */
    @Synchronized fun forget() {
        runCatching { store.save(null) }.onFailure { Log.w(TAG, "clearing paired device failed", it) }
        if (phase == Phase.STOPPED) return
        abortScan(); closeLink()
        _bpm.value = null; publishLost()
        phase = Phase.NO_DEVICE
    }

    // ---- state machine (all private functions run under the instance lock) ----

    private fun loadSaved(): SavedHrDevice? =
        runCatching { store.load() }.onFailure { Log.w(TAG, "loading paired device failed", it) }.getOrNull()

    private fun beginCycle(now: Long) {
        val savedDevice = loadSaved()
        if (savedDevice == null) { phase = Phase.NO_DEVICE; return }
        val available = runCatching { scanner.isBluetoothAvailable() }.getOrDefault(false)
        if (!available) { Log.w(TAG, "Bluetooth is off"); fail(now); return }
        connectTo(savedDevice, now, viaScan = false, fallback = false)
    }

    private fun connectTo(device: SavedHrDevice, now: Long, viaScan: Boolean, fallback: Boolean) {
        closeLink()
        try {
            val l = linkFactory(device.address)
            link = l
            l.start()
            lastFrames = l.framesReceived
        } catch (e: RuntimeException) {
            Log.w(TAG, "connect to ${device.address} failed", e)
            fail(now); return
        }
        attempt = device; attemptViaScan = viaScan; attemptIsFallback = fallback
        attemptStartedAt = now
        phase = Phase.CONNECTING
        Log.i(TAG, "connecting to ${device.address} (${device.name}) viaScan=$viaScan fallback=$fallback")
    }

    private fun startScan(now: Long) {
        phase = Phase.SCANNING
        found.clear(); scanFailure = null; scanStartedAt = now; scanActive = true
        try {
            scanner.startScan(
                onDeviceFound = { d -> synchronized(this) { if (phase == Phase.SCANNING) found += d } },
                onScanFailed = { r -> synchronized(this) { if (phase == Phase.SCANNING) scanFailure = r } },
            )
        } catch (e: RuntimeException) {
            scanFailure = e.message ?: "scan error"
        }
    }

    private fun abortScan() {
        if (scanActive) {
            scanActive = false
            runCatching { scanner.stopScan() }.onFailure { Log.w(TAG, "stopScan failed", it) }
        }
    }

    private fun closeLink() {
        runCatching { link?.stop() }.onFailure { Log.w(TAG, "link stop failed", it) }
        link = null
    }

    private fun fail(now: Long) {
        abortScan(); closeLink()
        failures++
        val delayMs = minOf(BACKOFF_BASE_MS shl (failures - 1).coerceAtMost(10), BACKOFF_MAX_MS)
        nextCycleAt = now + delayMs
        phase = Phase.WAITING
        publishLost()
        Log.i(TAG, "cycle failed ($failures), retry in ${delayMs}ms")
    }

    private fun publishLost() {
        _state.value = if (everConnected) ConnectionState.Disconnected else ConnectionState.Unavailable
    }

    /** Never throws: an exception would cancel the coroutine ticking us. */
    @Synchronized private fun onTick() {
        runCatching { tickOnce(clock()) }.onFailure { Log.w(TAG, "tick failed", it) }
    }

    private fun tickOnce(now: Long) {
        when (phase) {
            Phase.STOPPED -> Unit
            Phase.NO_DEVICE -> if (loadSaved() != null) beginCycle(now)
            Phase.WAITING -> if (now >= nextCycleAt) beginCycle(now)
            Phase.CONNECTING -> tickConnecting(now)
            Phase.SCANNING -> tickScanning(now)
            Phase.CONNECTED -> tickConnected(now)
        }
    }

    private fun tickConnecting(now: Long) {
        val l = link ?: return fail(now)
        if (l.framesReceived > 0) { onFirstFrame(l, now); return }
        if (l.failed) {
            Log.i(TAG, "link failed before first frame, giving up early")
            closeLink()
            if (attemptViaScan) fail(now) else startScan(now)
            return
        }
        if (now - attemptStartedAt >= CONNECT_TIMEOUT_MS) {
            closeLink()
            if (attemptViaScan) fail(now) else startScan(now)
        }
    }

    private fun onFirstFrame(l: HeartRateLink, now: Long) {
        phase = Phase.CONNECTED
        everConnected = true; failures = 0
        lastFrames = l.framesReceived; lastFrameAt = now
        _bpm.value = l.bpm.value; _state.value = ConnectionState.Connected
        val device = attempt
        if (device != null && attemptViaScan && !attemptIsFallback && loadSaved() != device) {
            runCatching { store.save(device) }.onFailure { Log.w(TAG, "saving rotated address failed", it) }
        }
    }

    private fun tickScanning(now: Long) {
        val failure = scanFailure
        if (failure != null) { Log.w(TAG, "scan failed: $failure"); fail(now); return }
        val savedDevice = loadSaved()
        val match = found.firstOrNull { d ->
            savedDevice != null && (d.address == savedDevice.address || (savedDevice.name != null && d.name == savedDevice.name))
        }
        if (match != null) {
            abortScan()
            connectTo(SavedHrDevice(match.address, match.name ?: savedDevice?.name), now, viaScan = true, fallback = false)
            return
        }
        if (now - scanStartedAt >= SCAN_WINDOW_MS) {
            val first = found.firstOrNull()
            abortScan()
            if (first == null) fail(now)
            else connectTo(SavedHrDevice(first.address, first.name), now, viaScan = true, fallback = true)
        }
    }

    private fun tickConnected(now: Long) {
        val l = link ?: return fail(now)
        if (l.connectionState.value != ConnectionState.Connected) { reconnectNow(now, "link dropped"); return }
        val frames = l.framesReceived
        if (frames != lastFrames) {
            lastFrames = frames; lastFrameAt = now
            _bpm.value = l.bpm.value; _state.value = ConnectionState.Connected
            return
        }
        val silentMs = now - lastFrameAt
        when {
            silentMs >= DEAD_MS -> reconnectNow(now, "no frame for ${silentMs}ms")
            silentMs >= STALE_MS -> { _bpm.value = null; _state.value = ConnectionState.Disconnected }
        }
    }

    private fun reconnectNow(now: Long, why: String) {
        Log.i(TAG, "reconnecting: $why")
        closeLink()
        _bpm.value = null; publishLost()
        failures = 0
        beginCycle(now)
    }

    companion object {
        const val TAG = "BleHeartRate"
        const val TICK_MS = 500L
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val SCAN_WINDOW_MS = 6_000L
        const val STALE_MS = 5_000L
        const val DEAD_MS = 15_000L
        const val BACKOFF_BASE_MS = 2_000L
        const val BACKOFF_MAX_MS = 30_000L
    }
}

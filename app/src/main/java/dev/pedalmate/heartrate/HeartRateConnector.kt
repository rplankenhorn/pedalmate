package dev.pedalmate.heartrate

import android.util.Log
import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.sensor.TickScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the saved heart-rate strap (or iPhone relay) connected, scan-first.
 *
 * With a saved device the connector runs ONE continuous filtered scan (0x180D service OR the saved
 * address OR the saved name, see [AndroidBleScanner]) and connects the moment a result matches the
 * saved address or name; the matched (possibly rotated) address is saved at match time. There is
 * no direct-connect first try, no first-device fallback and no unfiltered scan: replacing a strap
 * goes through PAIR HR STRAP. The scan is restarted every [SCAN_REFRESH_MS]. A connect that times
 * out ([CONNECT_TIMEOUT_MS]) or fails early, a link that drops, and a link silent for [DEAD_MS] are
 * closed and the scan resumes after [POST_CLOSE_DELAY_MS] (lets the stack release the GATT).
 * Rescans are at least [MIN_SCAN_INTERVAL_MS] apart and scan failures (permission, location
 * off, Bluetooth off) back off 2 s..30 s, which keeps the scanner under Android's 5-starts-per-30-s limit.
 * (A resume after pairing scans at once; that user-driven burst can still hit the limit and self-heals via the backoff.)
 *
 * An iPhone relay that is backgrounded advertises neither 0x180D nor its name, so no Android scan
 * sees it; the continuous scan catches it within seconds of it coming to the foreground, and the
 * long [DEAD_MS] keeps the link once made across the relay's bursty frames.
 *
 * [pauseScanning]/[resumeScanning] let the pairing scan run alone; pausing never closes a CONNECTED link.
 */
class HeartRateConnector(
    private val scanner: BleScanner,
    private val linkFactory: (address: String) -> HeartRateLink,
    private val store: SavedHrDeviceStore,
    private val clock: () -> Long,
    private val scheduler: TickScheduler,
) : ManagedHeartRateDataSource {

    private enum class Phase { STOPPED, NO_DEVICE, CONNECTING, SCANNING, CONNECTED, WAITING, PAUSED }

    private val _bpm = MutableStateFlow<Int?>(null)
    override val bpm: StateFlow<Int?> = _bpm.asStateFlow()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private var phase = Phase.STOPPED
    private var link: HeartRateLink? = null
    private var paused = false
    private var attemptStartedAt = 0L
    private var scanActive = false
    private var scanStartedAt = 0L
    private var lastScanStartAt = Long.MIN_VALUE / 2
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

    /** Pairing: remember [device] and start scanning for it now (the scan connects on a match). */
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

    /** Pairing scan is running: stop the connector's own scan (a CONNECTED link is left alone). */
    @Synchronized fun pauseScanning() {
        paused = true
        if (phase == Phase.SCANNING) { abortScan(); phase = Phase.PAUSED }
    }

    /** Pairing scan is over: scan again if the connector was not connected. */
    @Synchronized fun resumeScanning() {
        paused = false
        if (phase == Phase.PAUSED) beginCycle(clock())
    }

    // ---- state machine (all private functions run under the instance lock) ----

    private fun loadSaved(): SavedHrDevice? =
        runCatching { store.load() }.onFailure { Log.w(TAG, "loading paired device failed", it) }.getOrNull()

    private fun beginCycle(now: Long) {
        val savedDevice = loadSaved()
        if (savedDevice == null) { phase = Phase.NO_DEVICE; return }
        if (paused) { phase = Phase.PAUSED; return }
        val available = runCatching { scanner.isBluetoothAvailable() }.getOrDefault(false)
        if (!available) { Log.w(TAG, "Bluetooth is off"); fail(now); return }
        startScan(savedDevice, now)
    }

    private fun connectTo(device: SavedHrDevice, now: Long) {
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
        attemptStartedAt = now
        phase = Phase.CONNECTING
        Log.i(TAG, "connecting to ${device.address} (${device.name})")
    }

    private fun startScan(target: SavedHrDevice, now: Long) {
        phase = Phase.SCANNING
        found.clear(); scanFailure = null; scanStartedAt = now; lastScanStartAt = now; scanActive = true
        try {
            scanner.startScan(
                onDeviceFound = { d -> synchronized(this) { if (phase == Phase.SCANNING) found += d } },
                onScanFailed = { r -> synchronized(this) { if (phase == Phase.SCANNING) scanFailure = r } },
                target = ScanTarget(target.address, target.name),
                balanced = true,
            )
        } catch (e: RuntimeException) {
            scanFailure = e.message ?: "scan error"
        }
    }

    /**
     * Closes the link and scans again after [POST_CLOSE_DELAY_MS], but never sooner than
     * [MIN_SCAN_INTERVAL_MS] after the previous scan start, so a device that advertises yet whose
     * link keeps dying before a first frame cannot exceed 4 scan starts per 30 s.
     */
    private fun closeAndRescan(now: Long) {
        closeLink()
        nextCycleAt = maxOf(now + POST_CLOSE_DELAY_MS, lastScanStartAt + MIN_SCAN_INTERVAL_MS)
        phase = Phase.WAITING
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
            Phase.STOPPED, Phase.PAUSED -> Unit
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
            Log.i(TAG, "link failed before first frame, scanning again")
            closeAndRescan(now); publishLost()
            return
        }
        if (now - attemptStartedAt >= CONNECT_TIMEOUT_MS) {
            Log.i(TAG, "connect timed out, scanning again")
            closeAndRescan(now); publishLost()
        }
    }

    private fun onFirstFrame(l: HeartRateLink, now: Long) {
        phase = Phase.CONNECTED
        everConnected = true; failures = 0
        lastFrames = l.framesReceived; lastFrameAt = now
        _bpm.value = l.bpm.value; _state.value = ConnectionState.Connected
    }

    private fun tickScanning(now: Long) {
        val failure = scanFailure
        if (failure != null) { Log.w(TAG, "scan failed: $failure"); fail(now); return }
        val savedDevice = loadSaved() ?: run { abortScan(); phase = Phase.NO_DEVICE; return }
        val match = found.firstOrNull { d ->
            d.address == savedDevice.address || (savedDevice.name != null && d.name == savedDevice.name)
        }
        if (match != null) {
            abortScan()
            val device = SavedHrDevice(match.address, match.name ?: savedDevice.name)
            if (device != savedDevice) {
                runCatching { store.save(device) }.onFailure { Log.w(TAG, "saving rotated address failed", it) }
            }
            connectTo(device, now)
            return
        }
        if (now - scanStartedAt >= SCAN_REFRESH_MS) {
            abortScan()
            startScan(savedDevice, now)
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
        _bpm.value = null; publishLost()
        failures = 0
        closeAndRescan(now)
    }

    companion object {
        const val TAG = "BleHeartRate"
        const val TICK_MS = 500L
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val POST_CLOSE_DELAY_MS = 2_500L
        /** Floor between scan starts: 5 starts in 30 s trips Android's SCAN_FAILED_SCANNING_TOO_FREQUENTLY. */
        const val MIN_SCAN_INTERVAL_MS = 8_000L
        const val SCAN_REFRESH_MS = 300_000L
        const val STALE_MS = 5_000L
        /** Silence that tears the link down; the iPhone relay bursts, 60 s is the tunable if 45 s churns. */
        const val DEAD_MS = 45_000L
        const val BACKOFF_BASE_MS = 2_000L
        const val BACKOFF_MAX_MS = 30_000L
    }
}

// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/heartrate/BleHeartRateDataSource.kt. Modified by PedalMate.
package dev.pedalmate.heartrate

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.pedalmate.sensor.ConnectionState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real [HeartRateDataSource] for a single paired BLE strap, over the standard Bluetooth GATT
 * **Heart Rate Service** (`0x180D`) / **Heart Rate Measurement** characteristic (`0x2A37`) -
 * every commodity chest strap and most fitness watches implement this (PRD P1-4, T17).
 *
 * ## Protocol
 * - Connect via [android.bluetooth.BluetoothDevice.connectGatt], discover services, find the
 *   Heart Rate Measurement characteristic, enable notifications on it (write
 *   `ENABLE_NOTIFICATION_VALUE` to its Client Characteristic Configuration descriptor,
 *   `0x2902` - the standard GATT subscribe dance, not anything strap-specific).
 * - Each notification's raw bytes are decoded by [HeartRateParser] (reimplemented from the
 *   public Bluetooth SIG spec).
 *
 * ## Connection state
 * Same contract as [dev.pedalmate.sensor.PelotonBikeInterfaceDataSource]:
 * [ConnectionState.Connected] only once a real frame has been decoded (a GATT connection that
 * never yields data doesn't masquerade as live), [ConnectionState.Disconnected] on GATT
 * disconnect after having been connected, [ConnectionState.Unavailable] for anything that
 * prevents even trying (bad address, missing permission, adapter absent). [start] never
 * throws.
 *
 * **Hardware caveat (T17/#17): this project has no physical BLE heart-rate strap.** This class
 * is built correctly against the public `android.bluetooth` GATT client APIs and the
 * Bluetooth SIG spec, but the actual connect -> discover -> subscribe -> notify sequence has
 * **not been exercised against a real strap** - that needs someone with a physical device to
 * confirm. [HeartRateParser]'s byte-level decoding, which this class merely feeds real GATT
 * bytes into, *is* independently unit-tested (see [HeartRateParserTest]).
 */
class BleHeartRateDataSource internal constructor(
    private val context: Context,
    private val deviceAddress: String,
    private val connect: (BluetoothDevice, BluetoothGattCallback) -> GattClient?,
) : HeartRateLink {

    constructor(context: Context, deviceAddress: String) : this(context, deviceAddress, { device, cb ->
        device.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE)?.let(::RealGattClient)
    })

    @Volatile private var frames = 0L
    override val framesReceived: Long get() = frames

    private val _bpm = MutableStateFlow<Int?>(null)
    override val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile private var hasFailed = false
    override val failed: Boolean get() = hasFailed

    @Volatile private var gatt: GattClient? = null
    @Volatile private var closing = false
    @Volatile private var refreshed = false
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val closeFallback = Runnable { closeNow() }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) =
            handleConnectionState(newState)

        override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) = handleServicesDiscovered(status)

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            // The pre-API-33 callback signature (deprecated but still invoked by the platform
            // on every supported API level here, including 33+) - kept to one overload rather
            // than duplicating logic across both signatures.
            val bytes = characteristic?.value ?: return
            handleNotification(bytes)
        }
    }

    internal fun handleConnectionState(newState: Int) {
        if (closing) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) closeNow()
            // A CONNECTED that lands after stop(): tear it down so the fallback never closes a live link.
            if (newState == BluetoothProfile.STATE_CONNECTED) runCatching { gatt?.disconnect() }
            return
        }
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                try {
                    if (gatt?.discoverServices() == false) failRefused()
                } catch (e: SecurityException) {
                    Log.w(TAG, "discoverServices denied", e)
                    hasFailed = true
                    _connectionState.value = ConnectionState.Unavailable
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                val wasEverConnected = _connectionState.value == ConnectionState.Connected
                if (!wasEverConnected) hasFailed = true
                _connectionState.value = if (wasEverConnected) ConnectionState.Disconnected else ConnectionState.Unavailable
                releaseGatt()
            }
        }
    }

    internal fun handleServicesDiscovered(status: Int) {
        if (closing) return
        val g = gatt
        if (g == null) {
            fail("Heart Rate Measurement characteristic not found (status=$status)")
            return
        }
        try {
            if (status == BluetoothGatt.GATT_SUCCESS && g.getService(HEART_RATE_SERVICE_UUID) == null && !refreshed) {
                // A stale cached GATT db (no 0x180D): drop the cache once and rediscover.
                refreshed = true
                Log.i(TAG, "no Heart Rate service in the GATT db, refreshing")
                g.refresh()
                if (!g.discoverServices()) failRefused()
                return
            }
            val characteristic = if (status == BluetoothGatt.GATT_SUCCESS) {
                g.getService(HEART_RATE_SERVICE_UUID)?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            } else {
                null
            }
            if (characteristic == null) {
                fail("Heart Rate Measurement characteristic not found (status=$status)")
                return
            }
            g.enableNotifications(characteristic)
            // Stay Unavailable/Disconnected until a real frame arrives - see class doc.
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification subscribe denied", e)
            hasFailed = true
            _connectionState.value = ConnectionState.Unavailable
        }
    }

    internal fun handleNotification(bytes: ByteArray) {
        if (closing) return
        handleFrame(bytes)
    }

    private fun failRefused() {
        Log.w(TAG, "discoverServices refused")
        hasFailed = true
        _connectionState.value = ConnectionState.Unavailable
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        hasFailed = true
        _connectionState.value = ConnectionState.Unavailable
    }

    private fun handleFrame(bytes: ByteArray) {
        val measurement = try {
            HeartRateParser.parse(bytes)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Malformed Heart Rate Measurement frame, dropping", e)
            return
        }
        _bpm.value = measurement.bpm              // value first: a consumer that sees the new frame count must see its bpm
        frames++
        if (_connectionState.value != ConnectionState.Connected) {
            _connectionState.value = ConnectionState.Connected
        }
    }

    /**
     * Connects to [deviceAddress]. Safe on any device/state - an invalid address, a missing
     * adapter, or a missing runtime permission all degrade to [ConnectionState.Unavailable]
     * rather than throwing. Call once; call [stop] to release.
     */
    override fun start() {
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null) {
                hasFailed = true
                _connectionState.value = ConnectionState.Unavailable
                return
            }
            val device = adapter.getRemoteDevice(deviceAddress)
            gatt = connect(device, gattCallback)
            if (gatt == null) {
                hasFailed = true
                _connectionState.value = ConnectionState.Unavailable
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Connect denied - missing BLUETOOTH_CONNECT permission", e)
            hasFailed = true
            _connectionState.value = ConnectionState.Unavailable
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid BLE device address: $deviceAddress", e)
            hasFailed = true
            _connectionState.value = ConnectionState.Unavailable
        }
    }

    /**
     * Disconnects and releases the GATT client. The client is closed only once the stack reports
     * DISCONNECTED (or after [CLOSE_FALLBACK_MS]): closing straight after disconnect() unregisters
     * the client before the link is down, leaving the ACL to linger and poison the next connect.
     * Safe to call even if [start] never connected.
     */
    override fun stop() {
        _connectionState.value = ConnectionState.Unavailable
        if (gatt == null || closing) return
        closing = true
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
            // Permission revoked mid-session - fall through to the fallback close.
        }
        mainHandler.postDelayed(closeFallback, CLOSE_FALLBACK_MS)
    }

    @Synchronized private fun closeNow() {
        mainHandler.removeCallbacks(closeFallback)
        val g = gatt ?: return
        gatt = null
        try {
            g.close()
        } catch (_: SecurityException) {
        }
    }

    private fun releaseGatt() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {
            // Permission revoked mid-session - nothing more we can do, just drop the reference.
        }
        gatt = null
    }

    private companion object {
        const val TAG = "BleHeartRate"

        /**
         * Max wait for DISCONNECTED before close(). Below [HeartRateConnector.POST_CLOSE_DELAY_MS], so on the
         * retry paths (closeAndRescan/fail) the next connectGatt does not overlap a pending close; the
         * useDevice()/stop()->start() gap is tracked in bead pedalmate-vlo.
         */
        const val CLOSE_FALLBACK_MS = 2_000L

        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
    }
}

/** Narrow seam over [BluetoothGatt] so the stop/refresh sequencing can be unit tested with a fake. */
internal interface GattClient {
    fun discoverServices(): Boolean
    /** Drops the platform's cached GATT db (hidden API; false if unavailable). */
    fun refresh(): Boolean
    fun disconnect()
    fun close()
    fun getService(uuid: UUID): BluetoothGattService?
    /** Subscribes to notifications on [characteristic] (local flag plus the CCCD write). */
    fun enableNotifications(characteristic: BluetoothGattCharacteristic)
}

internal class RealGattClient(private val g: BluetoothGatt) : GattClient {
    override fun discoverServices() = g.discoverServices()

    override fun refresh(): Boolean = try {
        g.javaClass.getMethod("refresh").invoke(g) as? Boolean ?: false
    } catch (e: Exception) {
        Log.w("BleHeartRate", "GATT refresh unavailable", e)
        false
    }

    override fun disconnect() = g.disconnect()
    override fun close() = g.close()
    override fun getService(uuid: UUID): BluetoothGattService? = g.getService(uuid)

    override fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
        if (descriptor != null) {
            // The pre-API-33 CCCD-write path is deprecated, but minSdk is 29, so this is the
            // form that works across this app's whole supported range.
            @Suppress("DEPRECATION")
            run {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(descriptor)
            }
        }
    }
}

// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/heartrate/AndroidBleScanner.kt. Modified by PedalMate.
package dev.pedalmate.heartrate

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * Real [BleScanner] over `android.bluetooth`'s BLE scanning APIs, filtered to the standard
 * Heart Rate Service UUID (`0x180D`) so the pairing screen only lists relevant straps rather
 * than every BLE device in range (PRD P1-4, T17). When a [ScanTarget] is given (the connector
 * hunting for its saved strap) the filter list also holds that device's address and name; Android
 * ORs filters, so an iPhone relay whose rotated address or advertised name matches is found even
 * when its advertisement lacks the service UUID. Results are `CALLBACK_TYPE_ALL_MATCHES`, and
 * `balanced` selects `SCAN_MODE_BALANCED` for the connector's long-running scan (pairing keeps
 * `LOW_LATENCY`). An unfiltered scan is deliberately not offered.
 *
 * **Hardware caveat (T17/#17)**: this project has no physical BLE heart-rate strap to test
 * against, so this class is built correctly *per the public Android BLE APIs and Bluetooth SIG
 * spec* but is **not verified against a real device**. Every failure path (adapter null/off,
 * scanner unavailable, missing runtime permission, scan-start failure) degrades to
 * [onScanFailed] rather than throwing, matching the same "never crash, report unavailable"
 * contract [dev.pedalmate.sensor.PelotonBikeInterfaceDataSource] uses for the bike
 * sensor — but confirming an actual strap shows up in [startScan]'s results requires real
 * hardware in real proximity, which someone with a strap needs to verify.
 */
class AndroidBleScanner(private val context: Context) : BleScanner {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var scanCallback: ScanCallback? = null

    override fun isBluetoothAvailable(): Boolean =
        bluetoothAdapter?.isEnabled == true

    override fun startScan(
        onDeviceFound: (BleDevice) -> Unit,
        onScanFailed: (String) -> Unit,
        target: ScanTarget?,
        balanced: Boolean,
    ) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            onScanFailed("This device has no Bluetooth adapter")
            return
        }
        if (!adapter.isEnabled) {
            onScanFailed("Bluetooth is turned off")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onScanFailed("BLE scanning is unavailable on this device")
            return
        }

        val seenAddresses = mutableSetOf<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val address = device.address ?: return
                if (!seenAddresses.add(address)) return // already reported
                val name = try {
                    device.name
                } catch (_: SecurityException) {
                    null // BLUETOOTH_CONNECT not granted — address is still usable for pairing
                }
                onDeviceFound(BleDevice(address = address, name = name))
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed with error code $errorCode")
                onScanFailed("Scan failed (error code $errorCode)")
            }
        }
        scanCallback = callback

        // Android ORs the filters: a result matching any one is delivered.
        val filters = mutableListOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID)).build(),
        )
        if (target != null) {
            runCatching { ScanFilter.Builder().setDeviceAddress(target.address).build() }
                .onSuccess { filters += it }
                .onFailure { Log.w(TAG, "ignoring invalid saved address ${target.address}", it) }
            target.name?.let { filters += ScanFilter.Builder().setDeviceName(it).build() }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(if (balanced) ScanSettings.SCAN_MODE_BALANCED else ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            // Missing BLUETOOTH_SCAN (API 31+) or ACCESS_FINE_LOCATION (API <=30) at runtime —
            // the pairing screen is expected to request permissions before calling startScan,
            // but a race (permission revoked mid-session) shouldn't crash the app.
            Log.w(TAG, "BLE scan denied — missing runtime permission", e)
            scanCallback = null
            onScanFailed("Missing Bluetooth permission")
        }
    }

    override fun stopScan() {
        val adapter = bluetoothAdapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        val callback = scanCallback ?: return
        try {
            scanner.stopScan(callback)
        } catch (_: SecurityException) {
            // Same permission race as startScan; nothing to clean up beyond forgetting the
            // callback reference below.
        }
        scanCallback = null
    }

    private companion object {
        const val TAG = "BleHeartRate"

        /** Standard Bluetooth SIG Heart Rate Service UUID (0x180D), 16-bit UUID base form. */
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    }
}

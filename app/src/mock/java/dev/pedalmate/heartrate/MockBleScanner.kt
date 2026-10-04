package dev.pedalmate.heartrate

import android.os.Handler
import android.os.Looper

/** Emulator scanner: reports two fake straps so the pairing screen has something to list. */
class MockBleScanner : BleScanner {
    private val handler = Handler(Looper.getMainLooper())

    override fun isBluetoothAvailable(): Boolean = true

    override fun startScan(onDeviceFound: (BleDevice) -> Unit, onScanFailed: (String) -> Unit) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ onDeviceFound(BleDevice("00:11:22:33:44:01", "Mock HR 1")) }, 800L)
        handler.postDelayed({ onDeviceFound(BleDevice("00:11:22:33:44:02", "Mock HR 2")) }, 1_600L)
    }

    override fun stopScan() {
        handler.removeCallbacksAndMessages(null)
    }
}

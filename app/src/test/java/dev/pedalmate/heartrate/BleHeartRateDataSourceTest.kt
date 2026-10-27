package dev.pedalmate.heartrate

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Drives [BleHeartRateDataSource] through a fake [GattClient]. Robolectric's BluetoothGatt shadow was
 * not used: it cannot count the hidden refresh() call and fires callbacks synchronously from
 * disconnect(), so it cannot pin the deferred-close ordering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BleHeartRateDataSourceTest {
    private val hrService = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    private val hrMeasurement = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")

    private class FakeGatt : GattClient {
        var discoverCount = 0
        var refreshCount = 0
        var disconnectCount = 0
        var closeCount = 0
        var service: BluetoothGattService? = null
        var subscribed = 0
        override fun discoverServices(): Boolean { discoverCount++; return true }
        override fun refresh(): Boolean { refreshCount++; return true }
        override fun disconnect() { disconnectCount++ }
        override fun close() { closeCount++ }
        override fun getService(uuid: UUID): BluetoothGattService? = service
        override fun enableNotifications(characteristic: BluetoothGattCharacteristic) { subscribed++ }
    }

    private val gatt = FakeGatt()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val source = BleHeartRateDataSource(
        app, "00:11:22:33:44:55",
    ) { _, _ -> gatt }

    private fun hrServiceWithCharacteristic() = BluetoothGattService(hrService, BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
        it.addCharacteristic(BluetoothGattCharacteristic(hrMeasurement, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0))
    }

    private fun startConnected() {
        source.start()
        source.handleConnectionState(BluetoothProfile.STATE_CONNECTED)
    }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun `stop after connect disconnects and closes only on the DISCONNECTED callback`() {
        startConnected()
        gatt.service = hrServiceWithCharacteristic()
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS)
        source.stop()
        assertEquals(1, gatt.disconnectCount)
        assertEquals(0, gatt.closeCount)
        source.handleConnectionState(BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(1, gatt.closeCount)
        source.handleNotification(byteArrayOf(0x00, 72))
        assertNull(source.bpm.value)
        assertEquals(0L, source.framesReceived)
        advance(5_000)
        assertEquals(1, gatt.closeCount)
    }

    @Test fun `stop closes after the 2 s fallback without a callback`() {
        startConnected()
        source.stop()
        advance(1_999)
        assertEquals(0, gatt.closeCount)
        advance(1)
        assertEquals(1, gatt.closeCount)
        source.handleConnectionState(BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(1, gatt.closeCount)
    }

    @Test fun `callbacks after stop do not flip failed`() {
        startConnected()
        source.stop()
        source.handleServicesDiscovered(133)
        assertFalse(source.failed)
        assertEquals(0, gatt.refreshCount)
    }

    @Test fun `missing heart rate service refreshes once then fails`() {
        startConnected()
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS)
        assertEquals(1, gatt.refreshCount)
        assertEquals(2, gatt.discoverCount) // initial + after refresh
        assertFalse(source.failed)
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS)
        assertTrue(source.failed)
        assertEquals(1, gatt.refreshCount)
        assertEquals(2, gatt.discoverCount)
    }

    @Test fun `services discovered with status error fails without refresh`() {
        startConnected()
        source.handleServicesDiscovered(133)
        assertTrue(source.failed)
        assertEquals(0, gatt.refreshCount)
    }

    @Test fun `heart rate service present subscribes and delivers frames`() {
        startConnected()
        gatt.service = hrServiceWithCharacteristic()
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS)
        assertEquals(1, gatt.subscribed)
        assertFalse(source.failed)
        source.handleNotification(byteArrayOf(0x00, 72))
        assertEquals(72, source.bpm.value)
        assertEquals(1L, source.framesReceived)
    }
}

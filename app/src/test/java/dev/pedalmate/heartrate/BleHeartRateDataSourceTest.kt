package dev.pedalmate.heartrate

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
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
        var discoverResult = true
        var refreshCount = 0
        var disconnectCount = 0
        var closeCount = 0
        var service: BluetoothGattService? = null
        var subscribed = 0
        var enableResult = true
        override fun discoverServices(): Boolean { discoverCount++; return discoverResult }
        override fun refresh(): Boolean { refreshCount++; return true }
        override fun disconnect() { disconnectCount++ }
        override fun close() { closeCount++ }
        override fun getService(uuid: UUID): BluetoothGattService? = service
        override fun enableNotifications(characteristic: BluetoothGattCharacteristic): Boolean { subscribed++; return enableResult }
    }

    private val gatt = FakeGatt()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var cb: BluetoothGattCallback? = null
    private val source = BleHeartRateDataSource(
        app, "00:11:22:33:44:55",
    ) { _, callback -> cb = callback; gatt }

    private fun hrServiceWithCharacteristic() = BluetoothGattService(hrService, BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
        it.addCharacteristic(BluetoothGattCharacteristic(hrMeasurement, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0))
    }

    private fun startConnected() {
        source.start()
        source.handleConnectionState(BluetoothProfile.STATE_CONNECTED)
        advance(BleHeartRateDataSource.DISCOVER_DELAY_MS)
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
        assertFalse(source.failed)
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
        assertEquals(1, gatt.discoverCount)
    }

    @Test fun `heart rate service present subscribes and delivers frames through the real callback`() {
        source.start()
        cb!!.onConnectionStateChange(null, 0, BluetoothProfile.STATE_CONNECTED)
        advance(BleHeartRateDataSource.DISCOVER_DELAY_MS)
        assertEquals(1, gatt.discoverCount)
        gatt.service = hrServiceWithCharacteristic()
        cb!!.onServicesDiscovered(null, BluetoothGatt.GATT_SUCCESS)
        assertEquals(1, gatt.subscribed)
        assertFalse(source.failed)
        val c = BluetoothGattCharacteristic(hrMeasurement, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        @Suppress("DEPRECATION") c.value = byteArrayOf(0x00, 72)
        @Suppress("DEPRECATION") cb!!.onCharacteristicChanged(null, c)
        assertEquals(72, source.bpm.value)
        assertEquals(1L, source.framesReceived)
    }

    @Test fun `double stop disconnects once`() {
        startConnected()
        source.stop(); source.stop()
        assertEquals(1, gatt.disconnectCount)
        advance(5_000)
        assertEquals(1, gatt.closeCount)
    }

    @Test fun `late connected after stop re-issues disconnect and does not discover`() {
        startConnected()
        source.stop()
        val discovers = gatt.discoverCount
        source.handleConnectionState(BluetoothProfile.STATE_CONNECTED)
        advance(BleHeartRateDataSource.DISCOVER_DELAY_MS)
        assertEquals(2, gatt.disconnectCount)
        assertEquals(discovers, gatt.discoverCount)
        assertEquals(0, gatt.closeCount)
    }

    @Test fun `discoverServices refused fails fast`() {
        gatt.discoverResult = false
        startConnected()
        assertTrue(source.failed)
    }

    @Test fun `discoverServices refused after refresh fails fast`() {
        startConnected()
        gatt.discoverResult = false
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS)
        assertTrue(source.failed)
    }

    @Test fun `discovery is deferred by 300 ms after CONNECTED`() {
        source.start()
        source.handleConnectionState(BluetoothProfile.STATE_CONNECTED)
        assertEquals(0, gatt.discoverCount)
        advance(299)
        assertEquals(0, gatt.discoverCount)
        advance(1)
        assertEquals(1, gatt.discoverCount)
    }

    @Test fun `silent discovery is retried once after 3 s then fails after another 3 s`() {
        startConnected()
        advance(2_999)
        assertEquals(1, gatt.discoverCount)
        advance(1)
        assertEquals(2, gatt.discoverCount)
        assertFalse(source.failed)
        advance(2_999)
        assertFalse(source.failed)
        advance(1)
        assertTrue(source.failed)
        assertEquals(2, gatt.discoverCount)
    }

    @Test fun `services discovered cancels the discovery retry`() {
        startConnected()
        gatt.service = hrServiceWithCharacteristic()
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS)
        advance(10_000)
        assertEquals(1, gatt.discoverCount)
        assertFalse(source.failed)
    }

    @Test fun `stop before the delayed discovery fires never discovers`() {
        source.start()
        source.handleConnectionState(BluetoothProfile.STATE_CONNECTED)
        source.stop()
        advance(10_000)
        assertEquals(0, gatt.discoverCount)
        assertFalse(source.failed)
    }

    @Test fun `after DISCONNECTED the released gatt is never rediscovered`() {
        startConnected()
        source.handleConnectionState(BluetoothProfile.STATE_DISCONNECTED)
        val discovers = gatt.discoverCount
        advance(10_000)
        assertEquals(discovers, gatt.discoverCount)
    }

    @Test fun `refresh rediscovery is also retried when silent`() {
        startConnected()
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS) // no service: refresh + discover #2
        assertEquals(2, gatt.discoverCount)
        advance(3_000)
        assertEquals(3, gatt.discoverCount)
        advance(3_000)
        assertTrue(source.failed)
    }

    @Test fun `descriptor write success marks subscribed`() {
        startConnected()
        source.handleDescriptorWrite(BluetoothGatt.GATT_SUCCESS)
        assertTrue(source.subscribed)
        assertFalse(source.failed)
    }

    @Test fun `descriptor write failure fails`() {
        startConnected()
        source.handleDescriptorWrite(5)
        assertTrue(source.failed)
        assertFalse(source.subscribed)
    }

    @Test fun `CCCD write not issued fails`() {
        startConnected()
        gatt.service = hrServiceWithCharacteristic()
        gatt.enableResult = false
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS)
        assertTrue(source.failed)
    }

    @Test fun `descriptor write through the real callback marks subscribed`() {
        source.start()
        cb!!.onDescriptorWrite(null, null, 0)
        assertTrue(source.subscribed)
    }

    @Test fun `a second services discovered callback after the retry does not fail the link`() {
        startConnected()
        advance(3_000) // retry issued
        gatt.service = hrServiceWithCharacteristic()
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS)
        gatt.enableResult = false
        source.handleServicesDiscovered(BluetoothGatt.GATT_SUCCESS)
        assertEquals(1, gatt.subscribed)
        assertFalse(source.failed)
        advance(10_000)
        assertFalse(source.failed)
    }

    @Test fun `descriptor write after stop does not mark subscribed`() {
        startConnected()
        source.stop()
        source.handleDescriptorWrite(BluetoothGatt.GATT_SUCCESS)
        assertFalse(source.subscribed)
    }
}

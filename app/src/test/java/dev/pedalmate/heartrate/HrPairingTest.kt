package dev.pedalmate.heartrate

import dev.pedalmate.testutil.FakeHr
import dev.pedalmate.testutil.MemStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrPairingTest {
    @Test
    fun `use stores the device for a non-connector source`() {
        val store = MemStore()
        HrPairing(FakeHr(), store).use(BleDevice("A", "alpha"))
        assertEquals(SavedHrDevice("A", "alpha"), store.device)
    }

    @Test
    fun `forget clears the stored device for a non-connector source`() {
        val store = MemStore(SavedHrDevice("A", "alpha"))
        HrPairing(FakeHr(), store).forget()
        assertNull(store.device)
    }
}

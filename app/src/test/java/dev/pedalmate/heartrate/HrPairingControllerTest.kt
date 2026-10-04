package dev.pedalmate.heartrate

import dev.pedalmate.testutil.FakeHr
import dev.pedalmate.testutil.FakeScanner
import dev.pedalmate.testutil.MemStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HrPairingControllerTest {
    private val scanner = FakeScanner()
    private val store = MemStore()
    private val alpha = BleDevice("A", "alpha")
    private val zeta = BleDevice("B", "Zeta")

    private fun TestScope.controller() = HrPairingController(scanner, HrPairing(FakeHr(), store), backgroundScope)

    @Test
    fun `start begins scanning`() = runTest {
        val c = controller()
        c.startScan()
        assertEquals(PairingState.Scanning(emptyList()), c.state.value)
        assertEquals(1, scanner.startCount)
    }

    @Test
    fun `results are deduplicated and sorted`() = runTest {
        val c = controller()
        c.startScan()
        scanner.emit(BleDevice("B", "Zeta"))
        scanner.emit(BleDevice("A", "alpha"))
        scanner.emit(BleDevice("C", null))
        assertEquals(listOf(alpha, zeta, BleDevice("C", null)), (c.state.value as PairingState.Scanning).devices)
        scanner.emit(alpha)
        assertEquals(3, (c.state.value as PairingState.Scanning).devices.size)
        scanner.emit(BleDevice("C", "Strap"))
        assertEquals(listOf(alpha, BleDevice("C", "Strap"), zeta), (c.state.value as PairingState.Scanning).devices)
    }

    @Test
    fun `window end finishes the scan`() = runTest {
        val c = controller()
        c.startScan()
        scanner.emit(alpha)
        advanceTimeBy(9_999)
        assertTrue(c.state.value is PairingState.Scanning)
        advanceTimeBy(1); runCurrent()
        assertEquals(PairingState.Done(listOf(alpha)), c.state.value)
        assertEquals(1, scanner.stopCount)
    }

    @Test
    fun `failure stops the scan and sticks`() = runTest {
        val c = controller()
        c.startScan()
        scanner.fail("SCAN_FAILED_FEATURE_UNSUPPORTED")
        assertEquals(PairingState.Failed("SCAN_FAILED_FEATURE_UNSUPPORTED"), c.state.value)
        assertEquals(1, scanner.stopCount)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(PairingState.Failed("SCAN_FAILED_FEATURE_UNSUPPORTED"), c.state.value)
    }

    @Test
    fun `bluetooth unavailable`() = runTest {
        scanner.available = false
        val c = controller()
        c.startScan()
        assertTrue(c.state.value is PairingState.Unavailable)
        assertEquals(0, scanner.startCount)
    }

    @Test
    fun `double start is ignored`() = runTest {
        val c = controller()
        c.startScan()
        c.startScan()
        assertEquals(1, scanner.startCount)
    }

    @Test
    fun `stop returns to idle and ignores late work`() = runTest {
        val c = controller()
        c.startScan()
        c.stopScan()
        assertEquals(PairingState.Idle, c.state.value)
        assertEquals(1, scanner.stopCount)
        scanner.emit(alpha)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(PairingState.Idle, c.state.value)
    }

    @Test
    fun `select saves, stops the scan and reports paired`() = runTest {
        val c = controller()
        c.startScan()
        c.select(alpha)
        assertEquals(SavedHrDevice("A", "alpha"), store.device)
        assertEquals(1, scanner.stopCount)
        assertEquals(PairingState.Paired(alpha), c.state.value)
    }

    @Test
    fun `late result after the window is ignored`() = runTest {
        val c = controller()
        c.startScan()
        scanner.emit(alpha)
        advanceTimeBy(10_000); runCurrent()
        scanner.emit(zeta)
        assertEquals(PairingState.Done(listOf(alpha)), c.state.value)
    }

    @Test
    fun `new scan after done starts empty`() = runTest {
        val c = controller()
        c.startScan()
        scanner.emit(alpha)
        advanceTimeBy(10_000); runCurrent()
        c.startScan()
        assertEquals(PairingState.Scanning(emptyList()), c.state.value)
        assertEquals(2, scanner.startCount)
    }

    @Test
    fun `forget clears the saved device`() = runTest {
        store.device = SavedHrDevice("A", "alpha")
        val c = controller()
        c.forget()
        assertNull(store.device)
        assertEquals(PairingState.Idle, c.state.value)
    }
}

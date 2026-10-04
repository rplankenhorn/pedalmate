package dev.pedalmate.heartrate

import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.testutil.FakeClock
import dev.pedalmate.testutil.FakeLink
import dev.pedalmate.testutil.FakeLinkFactory
import dev.pedalmate.testutil.FakeScanner
import dev.pedalmate.testutil.FakeTickScheduler
import dev.pedalmate.testutil.MemStore
import dev.pedalmate.testutil.TickHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateConnectorTest {
    private val clock = FakeClock()
    private val scheduler = FakeTickScheduler()
    private val harness = TickHarness(clock, scheduler)
    private val scanner = FakeScanner()
    private val factory = FakeLinkFactory(clock)
    private val saved = SavedHrDevice("A", "HR-1")
    private val store = MemStore(saved)
    private val hr = HeartRateConnector(scanner, factory, store, clock, scheduler)
    private val state get() = hr.connectionState.value
    private val bpm get() = hr.bpm.value
    private fun tick(ms: Long) = harness.advanceBy(ms, stepMs = 500L)
    private fun connectToA(): FakeLink { hr.start(); val l = factory.created.last(); l.frame(72); tick(500); return l }

    @Test fun `no saved device stays Unavailable and never scans`() {
        store.device = null
        hr.start(); tick(10_000)
        assertEquals(0, scanner.startCount)
        assertTrue(factory.created.isEmpty())
        assertEquals(ConnectionState.Unavailable, state)
        assertNull(bpm)
        store.device = saved; tick(500)
        assertEquals(1, factory.created.size)
    }

    @Test fun `saved address hit connects without scanning`() {
        connectToA()
        assertEquals(ConnectionState.Connected, state)
        assertEquals(72, bpm)
        assertEquals(0, scanner.startCount)
        assertEquals(listOf("A"), factory.created.map { it.address })
        assertEquals(0, store.saves)
    }

    @Test fun `address miss falls back to a name match and saves the new address`() {
        hr.start(); tick(8_000)
        assertTrue(factory.created[0].stopped)
        assertEquals(1, scanner.startCount)
        scanner.emit(BleDevice("B", "Other")); tick(500)
        assertEquals(1, factory.created.size)
        scanner.emit(BleDevice("C", "HR-1")); tick(500)
        assertEquals("C", factory.created.last().address)
        assertFalse(scanner.scanning)
        factory.created.last().frame(70); tick(500)
        assertEquals(ConnectionState.Connected, state)
        assertEquals(70, bpm)
        assertEquals(SavedHrDevice("C", "HR-1"), store.device)
        assertEquals(1, store.saves)
    }

    @Test fun `first device is the fallback and does not overwrite the saved identity`() {
        hr.start(); tick(8_000)
        scanner.emit(BleDevice("B", null)); scanner.emit(BleDevice("D", "Another"))
        tick(6_000)
        assertEquals("B", factory.created.last().address)
        factory.created.last().frame(66); tick(500)
        assertEquals(ConnectionState.Connected, state)
        assertEquals(saved, store.device)
        assertEquals(0, store.saves)
    }

    @Test fun `a scan hit on the saved address connects at once`() {
        hr.start(); tick(8_000)
        scanner.emit(BleDevice("A", null)); tick(500)
        assertEquals(2, factory.created.size)
        assertEquals("A", factory.created[1].address)
    }

    @Test fun `empty scans back off 2 4 8 16 30 30 seconds and retry the saved address first`() {
        hr.start(); tick(180_000)
        assertEquals(
            listOf(0L, 16_000L, 34_000L, 56_000L, 86_000L, 130_000L, 174_000L),
            factory.created.map { it.createdAtMs },
        )
        assertTrue(factory.created.all { it.address == "A" })
    }

    @Test fun `scan failure such as location off backs off and retries without throwing`() {
        scanner.failOnStart = "Missing Bluetooth permission"
        hr.start(); tick(8_500)
        assertEquals(1, factory.created.size)
        tick(1_500)
        assertEquals(1, factory.created.size)
        tick(500)
        assertEquals(2, factory.created.size)
        assertEquals(ConnectionState.Unavailable, state)
    }

    @Test fun `scan failure while scanning gives the same backoff`() {
        hr.start(); tick(8_500)
        assertTrue(scanner.scanning)
        scanner.fail("Scan failed (error code 2)")
        tick(500)
        assertEquals(1, factory.created.size)
        tick(1_500)
        assertEquals(1, factory.created.size)
        tick(500)
        assertEquals(2, factory.created.size)
        assertEquals(ConnectionState.Unavailable, state)
    }

    @Test fun `Bluetooth off creates no links until it is back`() {
        scanner.available = false
        hr.start(); tick(5_000)
        assertTrue(factory.created.isEmpty())
        scanner.available = true; tick(1_000)
        assertEquals(1, factory.created.size)
    }

    @Test fun `disconnect retries the saved address immediately`() {
        val l = connectToA()
        l.drop(); tick(500)
        assertEquals(ConnectionState.Disconnected, state)
        assertNull(bpm)
        assertTrue(l.stopped)
        assertEquals(2, factory.created.size)
        assertEquals("A", factory.created[1].address)
    }

    @Test fun `identical frames keep it live and a silent strap goes stale after 5 s`() {
        hr.start()
        val l = factory.created.last()
        repeat(40) { l.frame(72); tick(500) }
        assertEquals(ConnectionState.Connected, state)
        assertEquals(72, bpm)
        tick(4_500)
        assertEquals(72, bpm)
        tick(500)
        assertNull(bpm)
        assertEquals(ConnectionState.Disconnected, state)
        assertFalse(l.stopped)
        assertEquals(1, factory.created.size)
        l.frame(70); tick(500)
        assertEquals(ConnectionState.Connected, state)
        assertEquals(70, bpm)
    }

    @Test fun `a link silent for 15 s is torn down and reconnected`() {
        hr.start()
        val l = factory.created.last()
        repeat(40) { l.frame(72); tick(500) }
        tick(5_000)
        tick(9_500)
        assertEquals(1, factory.created.size)
        tick(500)
        assertTrue(l.stopped)
        assertEquals(2, factory.created.size)
    }

    @Test fun `a received frame resets the backoff`() {
        hr.start(); tick(16_000)
        factory.created.last().frame(70); tick(500)
        factory.created.last().drop(); tick(500)
        tick(16_000)
        assertEquals(listOf(0L, 16_000L, 17_000L, 33_000L), factory.created.map { it.createdAtMs })
    }

    @Test fun `a scan-selected link that never delivers a frame times out and backs off`() {
        hr.start(); tick(8_000)
        scanner.emit(BleDevice("C", "HR-1")); tick(500)
        tick(8_000)
        tick(2_000)
        assertEquals(listOf(0L, 8_500L, 18_500L), factory.created.map { it.createdAtMs })
        assertEquals("A", factory.created.last().address)
    }

    @Test fun `useDevice saves and reconnects immediately`() {
        store.device = null
        hr.start(); tick(2_000)
        hr.useDevice(BleDevice("A", "HR-1"))
        assertEquals(SavedHrDevice("A", "HR-1"), store.device)
        assertEquals(1, factory.created.size)
        factory.created[0].frame(70); tick(500)
        hr.useDevice(BleDevice("B", "X"))
        assertTrue(factory.created[0].stopped)
        assertEquals("B", factory.created.last().address)
        assertEquals(ConnectionState.Disconnected, state)
        assertNull(bpm)
    }

    @Test fun `forget clears the store and disconnects`() {
        val l = connectToA()
        hr.forget()
        assertNull(store.device)
        assertTrue(l.stopped)
        assertNull(bpm)
        tick(5_000)
        assertEquals(1, factory.created.size)
    }

    @Test fun `stop releases everything`() {
        val l = connectToA()
        hr.stop()
        assertTrue(l.stopped)
        assertEquals(ConnectionState.Unavailable, state)
        assertNull(bpm)
        assertFalse(scheduler.running)
        hr.stop()
        hr.start()
        assertEquals(2, factory.created.size)
    }

    @Test fun `start is idempotent`() {
        hr.start(); hr.start()
        assertEquals(1, factory.created.size)
    }
}

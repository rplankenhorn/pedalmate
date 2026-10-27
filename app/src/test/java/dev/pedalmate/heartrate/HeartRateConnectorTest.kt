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
    init { scanner.clock = clock }
    private val state get() = hr.connectionState.value
    private val bpm get() = hr.bpm.value
    private fun tick(ms: Long) = harness.advanceBy(ms, stepMs = 500L)
    private val target = ScanTarget("A", "HR-1")
    /** Scans, finds the saved strap, and delivers a first frame. */
    private fun connectToA(): FakeLink {
        hr.start(); scanner.emit(BleDevice("A", "HR-1")); tick(500)
        val l = factory.created.last(); l.frame(72); tick(500); return l
    }

    @Test fun `no saved device stays Unavailable and never scans`() {
        store.device = null
        hr.start(); tick(10_000)
        assertEquals(0, scanner.startCount)
        assertTrue(factory.created.isEmpty())
        assertEquals(ConnectionState.Unavailable, state)
        assertNull(bpm)
        store.device = saved; tick(500)
        assertEquals(1, scanner.startCount)
    }

    @Test fun `start scans at once with the saved target and creates no link`() {
        hr.start()
        assertEquals(1, scanner.startCount)
        assertTrue(scanner.scanning)
        assertEquals(target, scanner.lastTarget)
        assertTrue(scanner.lastBalanced)
        assertTrue(factory.created.isEmpty())
        assertEquals(ConnectionState.Unavailable, state)
    }

    @Test fun `the first match on the saved address connects and stops the scan`() {
        connectToA()
        assertEquals(ConnectionState.Connected, state)
        assertEquals(72, bpm)
        assertEquals(listOf("A"), factory.created.map { it.address })
        assertFalse(scanner.scanning)
        assertEquals(0, store.saves)
    }

    @Test fun `a name match on a rotated address connects and saves the address at match time`() {
        hr.start()
        scanner.emit(BleDevice("B", "Other")); tick(500)
        assertTrue(factory.created.isEmpty())
        scanner.emit(BleDevice("C", "HR-1")); tick(500)
        assertEquals("C", factory.created.last().address)
        assertFalse(scanner.scanning)
        // saved before any frame arrives
        assertEquals(SavedHrDevice("C", "HR-1"), store.device)
        factory.created.last().frame(70); tick(500)
        assertEquals(ConnectionState.Connected, state)
        assertEquals(70, bpm)
        assertEquals(1, store.saves)
    }

    @Test fun `an unrelated device is never connected to`() {
        hr.start()
        scanner.emit(BleDevice("B", null)); scanner.emit(BleDevice("D", "Another"))
        tick(60_000)
        assertTrue(factory.created.isEmpty())
        assertEquals(saved, store.device)
    }

    @Test fun `no match keeps one scan for 2 minutes and refreshes it at 5 minutes`() {
        hr.start(); tick(120_000)
        assertEquals(1, scanner.startCount)
        assertEquals(0, scanner.stopCount)
        assertTrue(scanner.scanning)
        tick(180_000)
        assertEquals(2, scanner.startCount)
        assertEquals(1, scanner.stopCount)
        assertTrue(scanner.scanning)
        assertEquals(target, scanner.lastTarget)
        assertTrue(factory.created.isEmpty())
    }

    @Test fun `a device appearing at 60 s connects on the next tick`() {
        hr.start(); tick(60_000)
        scanner.emit(BleDevice("A", "HR-1"))
        assertTrue(factory.created.isEmpty())
        tick(500)
        assertEquals(1, factory.created.size)
        assertEquals(60_500L, factory.created[0].createdAtMs)
    }

    @Test fun `scan failure such as location off backs off 2 4 8 seconds and retries without throwing`() {
        scanner.failOnStart = "Missing Bluetooth permission"
        hr.start(); tick(500)
        assertEquals(1, scanner.startCount)
        tick(1_500)
        assertEquals(1, scanner.startCount)
        tick(500)
        assertEquals(2, scanner.startCount)
        tick(4_000)
        assertEquals(2, scanner.startCount)
        tick(500)
        assertEquals(3, scanner.startCount)
        assertEquals(ConnectionState.Unavailable, state)
        assertTrue(factory.created.isEmpty())
    }

    @Test fun `scan failure while scanning gives the same backoff`() {
        hr.start(); tick(8_500)
        assertTrue(scanner.scanning)
        scanner.fail("Scan failed (error code 2)")
        tick(500)
        assertEquals(1, scanner.startCount)
        tick(1_500)
        assertEquals(1, scanner.startCount)
        tick(500)
        assertEquals(2, scanner.startCount)
        assertEquals(ConnectionState.Unavailable, state)
    }

    @Test fun `scan failures back off up to 30 s`() {
        scanner.failOnStart = "boom"
        hr.start(); tick(180_000)
        // gaps of 2, 4, 8, 16, 30, 30 s plus tick granularity (failure is noticed on the next tick)
        assertEquals(listOf(0L, 2_500L, 7_000L, 15_500L, 32_000L, 62_500L, 93_000L, 123_500L, 154_000L), scanner.startTimes)
    }

    @Test fun `Bluetooth off starts no scan until it is back`() {
        scanner.available = false
        hr.start(); tick(5_000)
        assertEquals(0, scanner.startCount)
        scanner.available = true; tick(1_000)
        assertEquals(1, scanner.startCount)
    }

    @Test fun `a link drop closes it and scans again after 2_5 s`() {
        val l = connectToA()
        tick(10_000); l.frame(72); tick(500)
        l.drop(); tick(500)
        assertEquals(ConnectionState.Disconnected, state)
        assertNull(bpm)
        assertTrue(l.stopped)
        assertEquals(1, scanner.startCount)
        tick(2_000)
        assertEquals(1, scanner.startCount)
        tick(500)
        assertEquals(2, scanner.startCount)
        assertTrue(scanner.scanning)
        assertEquals(1, factory.created.size)
    }

    @Test fun `identical frames keep it live and a silent strap goes stale after 5 s`() {
        val l = connectToA()
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

    @Test fun `45 s of silence tears the link down and rescans after 2_5 s`() {
        val l = connectToA()
        tick(44_500)
        assertFalse(l.stopped)
        assertEquals(1, scanner.startCount)
        tick(500)
        assertTrue(l.stopped)
        assertEquals(1, scanner.startCount)
        tick(2_000)
        assertEquals(1, scanner.startCount)
        tick(500)
        assertEquals(2, scanner.startCount)
        assertEquals(1, factory.created.size)
    }

    @Test fun `40 s bursty silences never reconnect over 10 minutes`() {
        val l = connectToA()
        var sawNullBetweenBursts = false
        repeat(15) {
            l.frame(70); l.frame(71); tick(500)
            tick(39_500)
            if (bpm == null) sawNullBetweenBursts = true
        }
        assertTrue(sawNullBetweenBursts)
        assertFalse(l.stopped)
        assertEquals(1, factory.created.size)
        assertEquals(1, scanner.startCount)
    }

    @Test fun `a scan-selected link that never delivers a frame times out and rescans after 2_5 s`() {
        hr.start()
        scanner.emit(BleDevice("C", "HR-1")); tick(500)
        val l = factory.created.last()
        tick(7_500)
        assertFalse(l.stopped)
        tick(500)
        assertTrue(l.stopped)
        assertEquals(1, scanner.startCount)
        tick(2_000)
        assertEquals(1, scanner.startCount)
        tick(500)
        assertEquals(2, scanner.startCount)
        assertTrue(scanner.scanning)
        assertEquals(1, factory.created.size)
    }

    @Test fun `a subscribed link is not timed out by the 8 s rule and gets 15 s for its first frame`() {
        hr.start()
        scanner.emit(BleDevice("C", "HR-1")); tick(500)
        val l = factory.created.last()
        tick(1_500)
        l.subscribed = true
        tick(500) // subscribedAt = 2.5 s
        tick(6_000) // 8.5 s after the attempt start
        assertFalse(l.stopped)
        tick(8_000) // 16.5 s, 14 s after subscribe
        assertFalse(l.stopped)
        tick(500) // 17 s, 14.5 s after subscribe
        assertFalse(l.stopped)
        tick(500) // 17.5 s, 15 s after subscribe
        assertTrue(l.stopped)
        assertEquals(1, scanner.startCount)
        tick(2_500)
        assertEquals(2, scanner.startCount)
    }

    @Test fun `a subscribed link that delivers a frame connects as before`() {
        hr.start()
        scanner.emit(BleDevice("C", "HR-1")); tick(500)
        val l = factory.created.last()
        tick(1_500)
        l.subscribed = true
        tick(5_000)
        l.frame(80); tick(500)
        assertEquals(ConnectionState.Connected, state)
        assertEquals(80, bpm)
        assertFalse(l.stopped)
    }

    @Test fun `a scan-selected link that fails early rescans once the 8 s scan spacing has passed`() {
        hr.start()
        scanner.emit(BleDevice("C", "HR-1")); tick(500)
        val l = factory.created.last()
        tick(2_000)
        l.failNow(); tick(500)
        assertTrue(l.stopped)
        assertEquals(ConnectionState.Unavailable, state)
        tick(4_500)
        assertEquals(1, scanner.startCount)
        tick(500)
        assertEquals(2, scanner.startCount)
    }

    @Test fun `a link that keeps failing early never causes 5 scan starts in 30 s`() {
        hr.start()
        repeat(120) {
            tick(500)
            if (scanner.scanning) scanner.emit(BleDevice("A", "HR-1"))
            tick(500)
            factory.created.lastOrNull()?.takeIf { !it.stopped }?.failNow()
        }
        assertTrue(factory.created.size >= 5)
        val t = scanner.startTimes
        for (i in 0..t.size - 5) assertTrue("starts ${t.subList(i, i + 5)}", t[i + 4] - t[i] > 30_000L)
    }

    @Test fun `a later scan failure backs off from 2 s after a successful connection`() {
        scanner.failOnStart = "boom"
        hr.start(); tick(40_000)
        scanner.failOnStart = null; tick(35_000)
        scanner.emit(BleDevice("A", "HR-1")); tick(500)
        factory.created.last().frame(70); tick(500)
        tick(10_000)
        factory.created.last().frame(70); tick(500)
        scanner.failOnStart = "boom"
        factory.created.last().drop(); tick(3_000)
        // the rescan after the drop fails at once; with failures reset the retry comes 2 s (+500 ms detection) later
        tick(3_000)
        val t = scanner.startTimes
        assertEquals(2_500L, t[t.size - 1] - t[t.size - 2])
    }

    @Test fun `frames win over failed on the same tick`() {
        hr.start(); scanner.emit(BleDevice("A", "HR-1")); tick(500)
        val l = factory.created.last()
        l.failNow(); l.frame(80); tick(500)
        assertEquals(ConnectionState.Connected, state)
        assertEquals(1, scanner.startCount)
    }

    @Test fun `pause stops the scan and resume scans again`() {
        hr.start(); tick(1_000)
        hr.pauseScanning()
        assertFalse(scanner.scanning)
        tick(30_000)
        assertEquals(1, scanner.startCount)
        hr.resumeScanning()
        assertEquals(2, scanner.startCount)
        assertTrue(scanner.scanning)
        assertEquals(target, scanner.lastTarget)
    }

    @Test fun `pause does not close a connected link and resume starts no scan`() {
        val l = connectToA()
        hr.pauseScanning()
        assertFalse(l.stopped)
        assertEquals(ConnectionState.Connected, state)
        l.frame(75); tick(500)
        assertEquals(75, bpm)
        hr.resumeScanning()
        assertFalse(l.stopped)
        assertEquals(1, scanner.startCount)
        assertEquals(ConnectionState.Connected, state)
    }

    @Test fun `a drop while paused does not scan until resumed`() {
        val l = connectToA()
        hr.pauseScanning()
        l.drop(); tick(10_000)
        assertEquals(1, scanner.startCount)
        hr.resumeScanning()
        assertEquals(2, scanner.startCount)
    }

    @Test fun `useDevice saves and scans for the new target`() {
        store.device = null
        hr.start(); tick(2_000)
        assertEquals(0, scanner.startCount)
        hr.useDevice(BleDevice("A", "HR-1"))
        assertEquals(SavedHrDevice("A", "HR-1"), store.device)
        assertEquals(1, scanner.startCount)
        scanner.emit(BleDevice("A", "HR-1")); tick(500)
        factory.created[0].frame(70); tick(500)
        hr.useDevice(BleDevice("B", "X"))
        assertTrue(factory.created[0].stopped)
        assertEquals(ScanTarget("B", "X"), scanner.lastTarget)
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
        assertEquals(1, scanner.startCount)
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
        assertEquals(2, scanner.startCount)
    }

    @Test fun `stop ends a running scan`() {
        hr.start(); hr.stop()
        assertFalse(scanner.scanning)
    }

    @Test fun `start is idempotent`() {
        hr.start(); hr.start()
        assertEquals(1, scanner.startCount)
    }
}

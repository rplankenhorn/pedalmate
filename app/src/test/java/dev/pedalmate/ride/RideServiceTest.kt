package dev.pedalmate.ride

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.pedalmate.PedalMateApp
import dev.pedalmate.data.AppContainer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/** Drives the real [RideService] and app container under Robolectric; ride work runs on real Default threads. */
@RunWith(RobolectricTestRunner::class)
@Config(application = PedalMateApp::class)
class RideServiceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>() as PedalMateApp
    private val container: AppContainer get() = app.container
    private val controller: ServiceController<RideService> = Robolectric.buildService(RideService::class.java).create()
    private val service: RideService get() = controller.get()

    @After fun tearDown() {
        runBlocking { container.session.stop() }
        controller.destroy()
        container.scope.cancel()                  // async: it only requests cancellation; a NonCancellable stop may still reach a closed DB, which RideRecorder catches
        container.database.close()                // per-test container: release Room's connections (CloseGuard)
    }

    private fun send(action: String?, startId: Int, workoutId: String? = null): Int =
        service.onStartCommand(
            action?.let {
                Intent(app, RideService::class.java).setAction(it).also { i ->
                    if (workoutId != null) i.putExtra(RideService.EXTRA_WORKOUT_ID, workoutId)
                }
            },
            0,
            startId,
        )

    private fun finishWorkoutRide(startId: Int) {
        send(RideService.ACTION_START, startId, "pz-43")
        awaitUntil("workout running") { container.session.snapshot.value.status == RideStatus.RUNNING }
        var skips = 0
        while (container.session.snapshot.value.status != RideStatus.FINISHED) {
            check(++skips <= 20) { "workout did not finish after 20 skips" }
            val stepBefore = container.session.snapshot.value.workout?.stepIndex
            container.session.skip()
            awaitUntil("skip #$skips to be published") {
                val s = container.session.snapshot.value
                s.status == RideStatus.FINISHED || s.workout?.stepIndex != stepBefore
            }
        }
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 15_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))   // also advances the paused SystemClock the ticker reads
            check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            Thread.sleep(20)
        }
    }

    private fun stoppedWith(id: Int) = shadowOf(service as Service).stopSelfResultId == id

    @Test fun `stop then start leaves the new ride running`() {
        assertEquals(android.app.Service.START_STICKY, send(RideService.ACTION_STOP, 1))
        awaitUntil("stop #1 to settle") { stoppedWith(1) }
        send(RideService.ACTION_START, 2)
        awaitUntil("ride running") { container.session.snapshot.value.status == RideStatus.RUNNING }
        assertTrue(container.session.isActive)
        assertTrue("stop #1 completed and stopped with its own id", stoppedWith(1))
        assertFalse("the stale stop #1 must not be the last stop", stoppedWith(2))
    }

    /** Counts begins/finishes, fails on a begin while recording, and parks the first finish on [gate]. */
    private class GatedLog(val gate: CompletableDeferred<Unit>) : RideLog {
        @Volatile var recording = false
        val begins = AtomicInteger(); val finishes = AtomicInteger(); val beginWhileRecording = AtomicInteger()
        @Volatile var finishEntered = false
        override suspend fun begin(workoutId: String?, ftp: Int?): Long {
            if (recording) { beginWhileRecording.incrementAndGet(); throw IllegalStateException("already recording") }
            recording = true
            return begins.incrementAndGet().toLong()
        }
        override fun offer(frame: RideFrame) {}
        override suspend fun finish(): LiveAggregates? {
            finishEntered = true
            gate.await()
            recording = false; finishes.incrementAndGet()
            return null
        }
        override fun live(): LiveAggregates? = null
    }

    /** Swaps the container's session for one over [log]; the service reads `container.session` per command. */
    private fun useLog(log: RideLog) {
        val c = container
        val session = RideSession(c.hub, c.workouts, log, c.cuePlayer, { null }, c.scope)
        AppContainer::class.java.getDeclaredField("session").apply { isAccessible = true }.set(c, session)
    }

    @Test fun `a start right behind a stop waits for the finish then rides recorded`() {
        val gate = CompletableDeferred<Unit>()
        val log = GatedLog(gate)
        useLog(log)
        runBlocking { container.session.startFreeRide() }
        assertEquals(1, log.begins.get())
        send(RideService.ACTION_STOP, 1)
        send(RideService.ACTION_START, 2)          // the finish is still gated
        awaitUntil("finish to be entered") { log.finishEntered }
        assertEquals(1, log.begins.get())          // the start has not begun while the stop is running
        gate.complete(Unit)
        awaitUntil("new ride running") { log.beginWhileRecording.get() > 0 || (log.begins.get() == 2 && container.session.snapshot.value.status == RideStatus.RUNNING) }
        assertEquals("begin while recording", 0, log.beginWhileRecording.get())
        assertTrue(container.session.isActive)
        assertEquals(1, log.finishes.get())
        assertEquals(0, log.beginWhileRecording.get())
        assertTrue("stop #1 completed and stopped with its own id", stoppedWith(1))
        assertFalse("the stale stop #1 must not stop the service for the new ride", stoppedWith(2))
    }

    @Test fun `start then stop ends idle and stops with the stop startId`() {
        send(RideService.ACTION_START, 1)
        awaitUntil("ride running") { container.session.snapshot.value.status == RideStatus.RUNNING }
        send(RideService.ACTION_STOP, 2)
        awaitUntil("service stopped with #2") { stoppedWith(2) }
        assertEquals(RideStatus.IDLE, container.session.snapshot.value.status)
        assertFalse(container.session.isActive)
    }

    @Test fun `null intent restart stops itself when no ride is active`() {
        assertEquals(android.app.Service.START_STICKY, service.onStartCommand(null, 0, 1))
        awaitUntil("self stop") { stoppedWith(1) }
        assertEquals(RideStatus.IDLE, container.session.snapshot.value.status)
    }

    @Test fun `null intent restart continues an active ride and keeps ticking`() {
        runBlocking { container.session.startFreeRide() }
        assertTrue(container.session.isActive)
        service.onStartCommand(null, 0, 1)
        val before = container.session.snapshot.value.elapsedMs
        awaitUntil("ticker to advance elapsed time") { container.session.snapshot.value.elapsedMs > before + 500 }
        assertTrue(container.session.isActive)
        assertFalse(shadowOf(service as Service).isStoppedBySelf)
    }

    @Test fun `ride notification opens the setup screen`() {
        runBlocking { container.session.startFreeRide() }
        service.onStartCommand(null, 0, 1)         // CONTINUE: the foreground notification stays posted
        assertNotNull(shadowOf(service as Service).lastForegroundNotification.contentIntent)
    }

    @Test fun `a finished ride stops the service after the linger`() {
        service.finishedLingerMs = 300
        finishWorkoutRide(1)
        awaitUntil("service stopped with #1") { stoppedWith(1) }
        awaitUntil("session idle") { container.session.snapshot.value.status == RideStatus.IDLE }
        assertFalse(container.session.isActive)
    }

    @Test fun `a start during the linger cancels the auto-stop`() {
        service.finishedLingerMs = 500
        finishWorkoutRide(1)
        send(RideService.ACTION_START, 2)
        awaitUntil("free ride running") { container.session.snapshot.value.status == RideStatus.RUNNING }
        val end = System.currentTimeMillis() + 1_500
        awaitUntil("linger window to pass", timeoutMs = 5_000) { System.currentTimeMillis() >= end }
        assertTrue(container.session.isActive)
        assertEquals(RideStatus.RUNNING, container.session.snapshot.value.status)
        assertFalse(shadowOf(service as Service).isStoppedBySelf)
        assertFalse(stoppedWith(1))
        assertFalse(stoppedWith(2))
    }

    @Test fun `a refresh queued after stop does not leave the service running idle`() {
        runBlocking { container.session.startFreeRide() }
        assertTrue(container.session.isActive)
        send(RideService.ACTION_STOP, 1)
        send(RideService.ACTION_REFRESH_OVERLAY, 2)   // delivered while the session is still active
        awaitUntil("service stopped with #2") { stoppedWith(2) }
        assertFalse(container.session.isActive)
        assertTrue(shadowOf(service as Service).isStoppedBySelf)
    }
}

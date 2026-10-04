package dev.pedalmate.ride

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.pedalmate.PedalMateApp
import dev.pedalmate.data.AppContainer
import kotlinx.coroutines.runBlocking
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
    }

    private fun send(action: String?, startId: Int): Int =
        service.onStartCommand(action?.let { Intent(app, RideService::class.java).setAction(it) }, 0, startId)

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
        assertFalse("the stale stop #1 must not be the last stop", stoppedWith(2))
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
}

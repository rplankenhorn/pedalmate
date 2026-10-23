package dev.pedalmate.overlay

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.pedalmate.ride.RideSnapshot
import dev.pedalmate.ui.setup.SetupActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Drives real MotionEvents through the composed panel (no compose ui-test dependency in this project).
 * Runs at a large base density (10) so the 48 dp button dominates Robolectric's fixed ~36 px text line height and its
 * position is predictable: it is the trailing child of the interval strip, inside the panel's 20 dp / 16 dp padding.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayTouchTargetTest {
    private companion object {
        const val BASE_DENSITY = 10f
        val BaseDensity = Density(BASE_DENSITY, fontScale = 0.01f)
        const val PX_PER_DP = OVERLAY_SCALE * BASE_DENSITY
    }

    private class Harness(val host: ComposeOverlayHost, var openApp: Int = 0)

    private fun harness(): Harness {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val model = OverlayUiModel.from(RideSnapshot.idle(250))
        lateinit var h: Harness
        val host = ComposeOverlayHost(activity, { _, _ -> }, {}) {
            CompositionLocalProvider(LocalDensity provides BaseDensity) { OverlayContent(model) { h.openApp++ } }
        }
        h = Harness(host)
        activity.addContentView(host.root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        host.onAttached()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val unbounded = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)   // real 640 overlay-dp panel width
        host.root.measure(unbounded, unbounded)
        host.root.layout(0, 0, host.root.measuredWidth, host.root.measuredHeight)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return h
    }

    private fun Harness.tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        host.root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        host.root.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0))
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun buttonCenterX(h: Harness) = h.host.root.measuredWidth - (20 + 24) * PX_PER_DP
    private fun buttonCenterY() = (16 + 24) * PX_PER_DP

    @Test
    fun buttonSizeIs48OverlayDp() {
        assertEquals(48.dp, OpenAppButtonSize)
    }

    @Test
    fun tappingTheButtonOpensTheApp() {
        val h = harness()
        h.tap(buttonCenterX(h), buttonCenterY())
        assertEquals(1, h.openApp)
    }

    @Test
    fun buttonHitAreaIs48OverlayDp() {
        val h = harness()
        val half = 24 * PX_PER_DP
        h.tap(buttonCenterX(h) - half + 2f, buttonCenterY())
        h.tap(buttonCenterX(h) + half - 2f, buttonCenterY())
        h.tap(buttonCenterX(h), buttonCenterY() + half - 2f)
        assertEquals("taps just inside the 48 overlay-dp edge", 3, h.openApp)
    }

    @Test
    fun tappingThePanelBodyDoesNotOpenTheApp() {
        val h = harness()
        h.tap(h.host.root.measuredWidth / 2f, h.host.root.measuredHeight / 2f)
        h.tap(20f, h.host.root.measuredHeight - 20f)
        assertEquals(0, h.openApp)
    }

    @Test
    fun bringToFrontIntentTargetsSetupActivityAndReusesTheInstance() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val i = SetupActivity.bringToFrontIntent(ctx)
        assertEquals(SetupActivity::class.java.name, i.component?.className)
        assertEquals(ctx.packageName, i.component?.packageName)
        val want = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(want, i.flags and want)
    }
}

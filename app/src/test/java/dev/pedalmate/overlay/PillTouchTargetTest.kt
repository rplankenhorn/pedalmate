package dev.pedalmate.overlay

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.pedalmate.ride.RideSnapshot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Measures the real minimized pill through a [ComposeOverlayHost] attached to a Robolectric activity.
 * Robolectric text metrics dominate the pill height (about 36 px of line height whatever the font size),
 * so the measured check is a floor/sanity check; the constant test pins the actual 48 dp requirement.
 * (No compose ui-test dependency in this project; OverlayContent scales its own density, so the
 * expected minimum is 48 dp * OVERLAY_SCALE * the base density, in pixels.)
 */
@RunWith(RobolectricTestRunner::class)
class PillTouchTargetTest {
    @Test
    fun pillMinimumTouchTargetIs48Dp() {
        assertEquals(48.dp, PillMinTouchTarget)
    }

    @Test
    fun minimizedPillIsAtLeast48OverlayDpInBothDimensions() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        // empty text isolates the chrome (padding + dot) so the min-size modifier is what decides the result
        val model = OverlayUiModel.from(RideSnapshot.idle(250)).copy(pillText = "")
        val host = ComposeOverlayHost(activity, { _, _ -> }, {}) {
            // a vanishing font scale takes text height out of the equation, leaving only padding, dot and min size
            val tiny = Density(LocalDensity.current.density, fontScale = 0.01f)
            CompositionLocalProvider(LocalDensity provides tiny) { OverlayContent(model, true) {} }
        }
        activity.addContentView(host.root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        host.onAttached()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val unbounded = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        host.root.measure(unbounded, unbounded)
        val density = Density(activity.resources.displayMetrics.density).scaledForOverlay().density
        val minPx = Math.ceil((48f * density).toDouble() - 0.5).toInt()
        assertTrue("pill height ${host.root.measuredHeight} < $minPx", host.root.measuredHeight >= minPx)
        assertTrue("pill width ${host.root.measuredWidth} < $minPx", host.root.measuredWidth >= minPx)
    }
}

package dev.pedalmate.overlay

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.pedalmate.ride.RideSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.math.roundToInt

/**
 * Measures the real minimized pill through a [ComposeOverlayHost] attached to a Robolectric activity.
 * Robolectric text metrics dominate the pill height (about 36 px of line height whatever the font size),
 * so the measured check runs at a base density of 10 where the 48 dp floor dominates; the constant test is a documented guard.
 * (No compose ui-test dependency in this project; OverlayContent scales its own density, so the
 * expected minimum is 48 dp * OVERLAY_SCALE * the base density, in pixels.)
 */
@RunWith(RobolectricTestRunner::class)
class PillTouchTargetTest {
    private companion object {
        const val BASE_DENSITY = 10f
        val BaseDensity = Density(BASE_DENSITY, fontScale = 0.01f)
    }

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
            // Large base density makes the 48 dp floor (~278 px) dwarf padding, dot and Robolectric's fixed
            // ~36 px text line, so only the min-size modifier can satisfy it. Tiny fontScale removes text height.
            CompositionLocalProvider(LocalDensity provides BaseDensity) { OverlayContent(model, true) {} }
        }
        activity.addContentView(host.root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        host.onAttached()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val unbounded = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        host.root.measure(unbounded, unbounded)
        val minPx = (48f * OVERLAY_SCALE * BASE_DENSITY).roundToInt()
        assertTrue("pill height ${host.root.measuredHeight} < $minPx", host.root.measuredHeight >= minPx)
        assertTrue("pill width ${host.root.measuredWidth} < $minPx", host.root.measuredWidth >= minPx)
    }
}

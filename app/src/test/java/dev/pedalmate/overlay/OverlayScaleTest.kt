package dev.pedalmate.overlay

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayScaleTest {
    @Test
    fun scaledForOverlay_scalesDensityAndKeepsFontScale() {
        val scaled = Density(2f, 1.1f).scaledForOverlay()
        assertEquals(2f * 0.58f, scaled.density, 1e-6f)
        assertEquals(1.1f, scaled.fontScale, 1e-6f)
    }

    @Test
    fun overlayScale_isSane() {
        assertTrue(OVERLAY_SCALE > 0.5f && OVERLAY_SCALE < 0.8f)
    }
}

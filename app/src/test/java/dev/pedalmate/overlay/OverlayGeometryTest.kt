package dev.pedalmate.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGeometryTest {
    private val sw = 1920
    private val sh = 1080
    private val vw = 240
    private val vh = 300

    private fun clamp(x: Int, y: Int, w: Int = vw, h: Int = vh) =
        OverlayGeometry.clampToScreen(OverlayPosition(x, y), w, h, sw, sh)

    private fun snap(x: Int, y: Int = 0, w: Int = vw, screen: Int = sw) =
        OverlayGeometry.snapToEdge(OverlayPosition(x, y), w, screen, 48)

    @Test fun clampLeavesInsidePositionUnchanged() = assertEquals(OverlayPosition(100, 100), clamp(100, 100))
    @Test fun clampNegativeGoesToZero() = assertEquals(OverlayPosition(0, 0), clamp(-30, -5))
    @Test fun clampLargeGoesToMax() = assertEquals(OverlayPosition(1680, 780), clamp(2000, 2000))
    @Test fun clampViewWiderThanScreen() = assertEquals(0, clamp(500, 0, w = 2000).x)
    @Test fun clampViewTallerThanScreen() = assertEquals(0, clamp(0, 500, h = 1200).y)

    @Test fun snapLeftEdge() = assertEquals(0, snap(0).x)
    @Test fun snapJustInsideLeftThreshold() = assertEquals(0, snap(47).x)
    @Test fun snapAtLeftThresholdStays() = assertEquals(48, snap(48).x)
    @Test fun snapJustInsideRightThreshold() = assertEquals(1680, snap(1633).x)
    @Test fun snapAtRightThresholdStays() = assertEquals(1632, snap(1632).x)
    @Test fun snapNegative() = assertEquals(0, snap(-30).x)
    @Test fun snapBeyondRight() = assertEquals(1680, snap(2000).x)
    @Test fun snapMiddleStays() = assertEquals(900, snap(900).x)
    @Test fun snapNeverChangesY() = assertEquals(321, snap(900, 321).y)
    @Test fun snapTieLeftWins() = assertEquals(0, snap(30, w = 40, screen = 100).x)
    @Test fun snapViewWiderThanScreen() = assertEquals(0, snap(500, w = 2000).x)

    @Test fun defaultDocksRightAndCentersVertically() =
        assertEquals(OverlayPosition(1280, 390), OverlayGeometry.defaultPosition(640, 300, 1920, 1080))
    @Test fun defaultLargerThanScreenClampsToOrigin() =
        assertEquals(OverlayPosition(0, 0), OverlayGeometry.defaultPosition(2000, 1200, 1920, 1080))

    private fun re(x: Int, y: Int, prevW: Int, newW: Int, prevH: Int = 200, newH: Int = 200) =
        OverlayGeometry.reanchor(OverlayPosition(x, y), prevW, prevH, newW, newH, sw, sh)

    @Test fun reanchorPanelToPillKeepsVerticalCentre() = assertEquals(518, re(1680, 390, 640, 240, prevH = 300, newH = 44).y)
    @Test fun reanchorPillToPanelKeepsVerticalCentre() = assertEquals(390, re(1680, 518, 240, 640, prevH = 44, newH = 300).y)
    @Test fun reanchorVerticalCentreClampsAtBottom() = assertEquals(780, re(1680, 1000, 240, 640, prevH = 44, newH = 300).y)
    @Test fun reanchorVerticalCentreClampsAtTop() = assertEquals(0, re(1680, 0, 240, 640, prevH = 44, newH = 300).y)

    @Test fun reanchorDockedRightNarrower() = assertEquals(OverlayPosition(1800, 100), re(1680, 100, 240, 120))
    @Test fun reanchorDockedRightWider() = assertEquals(OverlayPosition(1520, 100), re(1680, 100, 240, 400))
    @Test fun reanchorDockedLeft() = assertEquals(OverlayPosition(0, 100), re(0, 100, 240, 120))
    @Test fun reanchorMidKeepsX() = assertEquals(OverlayPosition(500, 100), re(500, 100, 240, 120))
    @Test fun reanchorMidClamped() = assertEquals(OverlayPosition(920, 100), re(1000, 100, 240, 1000))
    @Test fun reanchorFullWidthIsNotDockedRight() = assertEquals(OverlayPosition(0, 5), re(0, 5, 1920, 240))
}

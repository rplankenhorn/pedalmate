package dev.pedalmate.overlay

data class OverlayPosition(val x: Int, val y: Int)

/** A position plus the view size it was recorded at, so a later layout of a different size can re-dock it. */
data class OverlayPlacement(val position: OverlayPosition, val viewW: Int, val viewH: Int)

/** Pure placement maths for the overlay window (pixels, top-left origin). */
object OverlayGeometry {
    const val SNAP_THRESHOLD_PX = 48

    private fun maxCoord(screen: Int, view: Int) = maxOf(0, screen - view)

    fun clampToScreen(pos: OverlayPosition, viewW: Int, viewH: Int, screenW: Int, screenH: Int) = OverlayPosition(
        x = pos.x.coerceIn(0, maxCoord(screenW, viewW)),
        y = pos.y.coerceIn(0, maxCoord(screenH, viewH)),
    )

    fun snapToEdge(pos: OverlayPosition, viewW: Int, screenW: Int, thresholdPx: Int = SNAP_THRESHOLD_PX): OverlayPosition {
        val maxX = maxCoord(screenW, viewW)
        val x = pos.x.coerceIn(0, maxX)
        val snapped = when {
            x < thresholdPx -> 0
            maxX - x < thresholdPx -> maxX
            else -> x
        }
        return OverlayPosition(snapped, pos.y)
    }

    /** Right-docked and vertically centered, between the bike's top and bottom clocks. */
    fun defaultPosition(viewW: Int, viewH: Int, screenW: Int, screenH: Int) =
        OverlayPosition(maxCoord(screenW, viewW), maxCoord(screenH, viewH) / 2)

    /** Keeps the right/left docking (x) and the vertical centre (y) when the view changes size. */
    fun reanchor(prev: OverlayPosition, prevW: Int, prevH: Int, newW: Int, newH: Int, screenW: Int, screenH: Int): OverlayPosition {
        val prevMax = maxCoord(screenW, prevW)
        val dockedRight = prevMax > 0 && prev.x == prevMax
        val newMax = maxCoord(screenW, newW)
        val y = (prev.y + prevH / 2 - newH / 2).coerceIn(0, maxCoord(screenH, newH))
        return OverlayPosition(if (dockedRight) newMax else prev.x.coerceIn(0, newMax), y)
    }
}

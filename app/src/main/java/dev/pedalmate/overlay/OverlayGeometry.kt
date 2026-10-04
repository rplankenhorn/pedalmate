package dev.pedalmate.overlay

data class OverlayPosition(val x: Int, val y: Int)

/** Pure placement maths for the overlay window (pixels, top-left origin). */
object OverlayGeometry {
    const val SNAP_THRESHOLD_PX = 48
    const val DEFAULT_TOP_MARGIN_PX = 80

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

    fun defaultPosition(viewW: Int, screenW: Int, topMarginPx: Int = DEFAULT_TOP_MARGIN_PX) =
        OverlayPosition(maxCoord(screenW, viewW), topMarginPx)

    fun reanchor(prev: OverlayPosition, prevW: Int, newW: Int, screenW: Int): OverlayPosition {
        val prevMax = maxCoord(screenW, prevW)
        val dockedRight = prevMax > 0 && prev.x == prevMax
        val newMax = maxCoord(screenW, newW)
        return OverlayPosition(if (dockedRight) newMax else prev.x.coerceIn(0, newMax), prev.y)
    }
}

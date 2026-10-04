package dev.pedalmate.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Container that turns a touch into a window drag once it has moved past the touch slop; until then
 * the touch belongs to the children (Compose), so taps still work. Uses raw screen coordinates
 * because the window itself moves under the finger.
 */
class DraggableFrameLayout(
    context: Context,
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> recordDown(ev)
            MotionEvent.ACTION_MOVE -> if (!dragging) maybeStartDrag(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { recordDown(ev); return true }
                MotionEvent.ACTION_MOVE -> maybeStartDrag(ev)
            }
            if (!dragging) return super.onTouchEvent(ev)
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> emitDelta(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; onDragEnd() }
        }
        return true
    }

    private fun recordDown(ev: MotionEvent) {
        downX = ev.rawX; downY = ev.rawY
        lastX = downX; lastY = downY
        dragging = false
    }

    private fun maybeStartDrag(ev: MotionEvent) {
        if (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop) {
            dragging = true
            emitDelta(ev)
        }
    }

    private fun emitDelta(ev: MotionEvent) {
        val dx = (ev.rawX - lastX).toInt()
        val dy = (ev.rawY - lastY).toInt()
        // Advance by what was emitted, not to rawX/rawY, so the sub-pixel remainder carries to the next move.
        lastX += dx; lastY += dy
        if (dx != 0 || dy != 0) onDrag(dx, dy)
    }
}

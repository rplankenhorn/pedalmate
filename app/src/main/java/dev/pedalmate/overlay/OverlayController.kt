package dev.pedalmate.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import android.provider.Settings as AndroidSettings

/** Shows one draggable Compose panel over other apps, remembering placement rules (dock right, snap to edges). */
class OverlayController(
    private val context: Context,
    windowManager: WindowManager,
    canDraw: () -> Boolean = { AndroidSettings.canDrawOverlays(context) },
    private val screenSize: () -> Pair<Int, Int> = {
        val m = context.resources.displayMetrics
        m.widthPixels to m.heightPixels
    },
) {
    private val window = OverlayWindow(windowManager, canDraw)
    private var params: WindowManager.LayoutParams? = null
    private var keepScreenOn = false
    private var onMoved: (OverlayPlacement) -> Unit = {}
    private var initial: OverlayPlacement? = null
    private var placed = false // true after the first layout decided the position
    private var viewW = 0
    private var viewH = 0
    val isShowing: Boolean get() = window.isShowing

    fun show(content: @Composable () -> Unit, initial: OverlayPlacement?, onMoved: (OverlayPlacement) -> Unit): ShowResult {
        if (window.isShowing) return ShowResult.ALREADY_SHOWN
        this.onMoved = onMoved
        placed = false
        this.initial = initial
        viewW = 0
        viewH = 0
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initial?.position?.x ?: screenSize().first // WindowManager clamps; the first layout corrects it
            y = initial?.position?.y ?: screenSize().second / 2 // placeholder; the first layout centers it
        }
        val host = ComposeOverlayHost(context, ::dragBy, ::dragEnded, content)
        host.root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            onLayout(right - left, bottom - top, oldRight - oldLeft, oldBottom - oldTop)
        }
        params = p
        val result = window.show(host, p)
        if (result != ShowResult.SHOWN) params = null
        return result
    }

    fun hide() {
        window.hide()
        params = null
    }

    /** Remembered when called before [show]. */
    fun setKeepScreenOn(on: Boolean) {
        if (keepScreenOn == on) return
        keepScreenOn = on
        val p = params ?: return
        p.flags = flags()
        window.update(p)
    }

    private fun flags() = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        (if (keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0)

    private fun onLayout(w: Int, h: Int, prevW: Int, prevH: Int) {
        val p = params ?: return
        viewW = w
        viewH = h
        if (w <= 0) return
        val (sw, sh) = screenSize()
        val next = when {
            !placed -> {
                placed = true
                val saved = initial
                when {
                    // no saved size (null, or prefs written before R3 with a stale top-left): default dock
                    saved == null || saved.viewW <= 0 -> OverlayGeometry.defaultPosition(w, h, sw, sh)
                    else -> OverlayGeometry.clampToScreen(
                        OverlayGeometry.reanchor(saved.position, saved.viewW, saved.viewH, w, h, sw, sh), w, h, sw, sh,
                    )
                }
            }
            prevW > 0 && prevW != w -> OverlayGeometry.clampToScreen(
                OverlayGeometry.reanchor(OverlayPosition(p.x, p.y), prevW, prevH, w, h, sw, sh), w, h, sw, sh,
            )
            else -> return
        }
        if (next.x != p.x || next.y != p.y) {
            p.x = next.x
            p.y = next.y
            window.update(p)
        }
    }

    private fun dragBy(dx: Int, dy: Int) {
        val p = params ?: return
        val (sw, sh) = screenSize()
        val next = OverlayGeometry.clampToScreen(OverlayPosition(p.x + dx, p.y + dy), viewW, viewH, sw, sh)
        p.x = next.x
        p.y = next.y
        window.update(p)
    }

    private fun dragEnded() {
        val p = params ?: return
        val (sw, _) = screenSize()
        val snapped = OverlayGeometry.snapToEdge(OverlayPosition(p.x, p.y), viewW, sw)
        p.x = snapped.x
        p.y = snapped.y
        window.update(p)
        onMoved(OverlayPlacement(snapped, viewW, viewH))
    }
}

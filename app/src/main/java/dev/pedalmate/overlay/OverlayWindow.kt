package dev.pedalmate.overlay

import android.util.Log
import android.view.WindowManager

enum class ShowResult { SHOWN, ALREADY_SHOWN, NO_PERMISSION, FAILED }

/** The one place that calls WindowManager.addView/removeView for an overlay window. Main thread only. */
class OverlayWindow(private val windowManager: WindowManager, private val canDraw: () -> Boolean) {
    private var host: ComposeOverlayHost? = null
    val isShowing: Boolean get() = host != null

    fun show(host: ComposeOverlayHost, params: WindowManager.LayoutParams): ShowResult {
        if (this.host != null) { host.destroy(); return ShowResult.ALREADY_SHOWN }
        if (!canDraw()) { host.destroy(); return ShowResult.NO_PERMISSION }
        return try {
            windowManager.addView(host.root, params)
            host.onAttached()
            this.host = host
            ShowResult.SHOWN
        } catch (e: RuntimeException) {
            Log.w(TAG, "overlay addView failed", e)
            host.destroy()
            ShowResult.FAILED
        }
    }

    fun update(params: WindowManager.LayoutParams) {
        val h = host ?: return
        try {
            windowManager.updateViewLayout(h.root, params)
        } catch (e: RuntimeException) {
            Log.w(TAG, "overlay update failed", e)
        }
    }

    fun hide() {
        val h = host ?: return
        host = null
        try {
            windowManager.removeViewImmediate(h.root)
        } catch (e: RuntimeException) {
            Log.w(TAG, "overlay remove failed", e)
        }
        h.destroy()
    }

    private companion object {
        const val TAG = "PedalMate"
    }
}

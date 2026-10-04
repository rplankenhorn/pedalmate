package dev.pedalmate.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centred, untouchable overlay window for the interval toast. Reuses [OverlayWindow] and
 * [ComposeOverlayHost]; a second [show] only updates the content. Main thread only.
 */
class OverlayToastWindow(
    private val context: Context,
    windowManager: WindowManager,
    canDraw: () -> Boolean = { Settings.canDrawOverlays(context) },
) : ToastWindow {
    private val window = OverlayWindow(windowManager, canDraw)
    private val current = mutableStateOf<ToastModel?>(null)

    override fun show(model: ToastModel) {
        current.value = model
        if (window.isShowing) return                      // content updates through the state
        val host = ComposeOverlayHost(context, { _, _ -> }, {}) { ToastContent(current) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        val result = window.show(host, params)
        if (result != ShowResult.SHOWN) Log.w("PedalMate", "interval toast not shown: $result")
    }

    override fun hide() {
        window.hide()
        current.value = null
    }
}

@Composable
private fun ToastContent(state: State<ToastModel?>) {
    val m = state.value ?: return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xE6000000), RoundedCornerShape(20.dp))
            .padding(horizontal = 56.dp, vertical = 32.dp),
    ) {
        Text(m.heading, fontSize = 24.sp, color = Color.White.copy(alpha = 0.7f))
        Text(m.title, fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.White)
        m.rangeText?.let { Text(it, fontSize = 40.sp, color = Color.White) }
        Text(m.durationText, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

package dev.pedalmate.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Something that can display one [ToastModel] at a time. */
interface ToastWindow {
    fun show(model: ToastModel)
    fun hide()
}

/** One toast at a time: a new one replaces the current one and restarts the timer. Main thread only. */
class IntervalToast(
    private val scope: CoroutineScope,
    private val window: ToastWindow,
    private val dismissAfterMs: Long = 4_000L,
) {
    private var timer: Job? = null

    fun show(model: ToastModel) {
        timer?.cancel()
        window.show(model)
        timer = scope.launch { delay(dismissAfterMs); window.hide() }
    }

    fun cancel() {
        timer?.cancel(); timer = null
        window.hide()
    }
}

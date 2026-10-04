package dev.pedalmate.sensor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Repeats a callback at a fixed period; injected so state machines are testable without real time. */
interface TickScheduler {
    /** Starts calling [tick] every [periodMs] until [stop]. A second start replaces the first. */
    fun start(periodMs: Long, tick: () -> Unit)

    /** Stops ticking. Safe to call when not started. */
    fun stop()
}

/** Production [TickScheduler] backed by a coroutine in [scope]. */
class CoroutineTickScheduler(private val scope: CoroutineScope) : TickScheduler {
    private var job: Job? = null

    @Synchronized
    override fun start(periodMs: Long, tick: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(periodMs)
                tick()
            }
        }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
    }
}

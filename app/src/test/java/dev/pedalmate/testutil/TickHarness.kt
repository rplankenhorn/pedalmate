package dev.pedalmate.testutil

import dev.pedalmate.sensor.TickScheduler

/** Settable monotonic clock for deterministic tests. */
class FakeClock(var nowMs: Long = 0L) : () -> Long {
    override fun invoke(): Long = nowMs
}

/** Scheduler whose ticks are fired by the test, never by real time. */
class FakeTickScheduler : TickScheduler {
    var running = false
        private set
    var periodMs = 0L
        private set
    private var tick: (() -> Unit)? = null

    override fun start(periodMs: Long, tick: () -> Unit) {
        this.periodMs = periodMs
        this.tick = tick
        running = true
    }

    override fun stop() {
        running = false
        tick = null
    }

    fun fire() {
        if (running) tick?.invoke()
    }
}

/** Advances [clock] in [stepMs] steps, firing one scheduler tick after each step. */
class TickHarness(val clock: FakeClock, val scheduler: FakeTickScheduler) {
    fun advanceBy(ms: Long, stepMs: Long = 250L) {
        var left = ms
        while (left > 0) {
            val step = minOf(stepMs, left)
            clock.nowMs += step
            scheduler.fire()
            left -= step
        }
    }
}

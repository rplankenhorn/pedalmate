package dev.pedalmate.sensor

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps the Bike+ source with two safety nets:
 *  - if no frame is PUSHED for [PUSH_SILENCE_MS], poll `getBikeData` at 1 Hz until pushes resume;
 *  - if no frame (pushed or polled) arrives for [DISCONNECT_MS], report [ConnectionState.Disconnected].
 * Liveness comes from [PollableBikeDataSource.framesReceived], never from StateFlow emissions,
 * because identical consecutive frames are legitimate and a StateFlow would swallow them.
 * Time comes from the injected [clock] (monotonic ms) and [scheduler]; no real time in here.
 */
class BikeSourceSupervisor(
    private val source: PollableBikeDataSource,
    private val clock: () -> Long,
    private val scheduler: TickScheduler,
) : BoundBikeDataSource {

    override val metrics: StateFlow<BikeMetrics> get() = source.metrics

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private var framesSeen = 0L
    private var lastPushAt = 0L
    private var lastFrameAt = 0L
    private var lastPollAt = 0L
    private var lastLogAt = 0L
    private var everFrame = false
    private var polling = false

    @Synchronized
    override fun start() {
        val now = clock()
        framesSeen = source.framesReceived
        lastPushAt = now; lastFrameAt = now; lastPollAt = now; lastLogAt = now
        everFrame = false
        polling = false
        _state.value = ConnectionState.Unavailable
        source.start()
        scheduler.start(TICK_MS) { onTick() }
    }

    @Synchronized
    override fun stop() {
        scheduler.stop()
        source.stop()
        polling = false
        everFrame = false
        _state.value = ConnectionState.Unavailable
    }

    @Synchronized
    private fun onTick() {
        val now = clock()
        val count = source.framesReceived
        if (count != framesSeen) {                       // frames were pushed since we last looked
            framesSeen = count
            lastPushAt = now; lastFrameAt = now; everFrame = true
            if (polling) { polling = false; Log.i(TAG, "push resumed, polling stopped") }
        }
        if (!polling && now - lastPushAt >= PUSH_SILENCE_MS) {
            polling = true
            lastPollAt = now - POLL_PERIOD_MS            // first poll happens on this tick
            Log.i(TAG, "no push for ${PUSH_SILENCE_MS}ms, polling getBikeData at 1 Hz")
        }
        if (polling && now - lastPollAt >= POLL_PERIOD_MS) {
            lastPollAt = now
            if (source.pollBikeData() != null) { lastFrameAt = now; everFrame = true }
            framesSeen = source.framesReceived           // our own poll is not a push
        }
        _state.value = when {
            everFrame && now - lastFrameAt < DISCONNECT_MS -> ConnectionState.Connected
            everFrame -> ConnectionState.Disconnected
            else -> ConnectionState.Unavailable
        }
        if (now - lastLogAt >= LOG_EVERY_MS) {
            lastLogAt = now
            Log.i(TAG, "bike frames=${source.framesReceived} state=${_state.value} polling=$polling")
        }
    }

    companion object {
        private const val TAG = "PedalMate"
        const val TICK_MS = 250L
        const val PUSH_SILENCE_MS = 5_000L
        const val POLL_PERIOD_MS = 1_000L
        const val DISCONNECT_MS = 3_000L
        private const val LOG_EVERY_MS = 5_000L
    }
}

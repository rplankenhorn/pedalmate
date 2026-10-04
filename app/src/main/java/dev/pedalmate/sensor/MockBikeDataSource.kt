package dev.pedalmate.sensor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Emulator stand-in for the Bike+: replays [profile] at [tickMs]; script time counts ticks, not wall time. */
class MockBikeDataSource(
    private val profile: ScriptedProfile,
    private val scope: CoroutineScope,
    private val tickMs: Long = 250L,
) : BoundBikeDataSource {
    private val _metrics = MutableStateFlow(BikeMetrics.ZERO)
    override val metrics: StateFlow<BikeMetrics> = _metrics.asStateFlow()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()
    private var job: Job? = null

    @Synchronized
    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var tick = 0L
            while (isActive) {
                val sample = profile.sample((tick * tickMs / 1_000L).toInt())
                if (sample != null) {
                    _metrics.value = sample
                    _state.value = ConnectionState.Connected
                } else {
                    _state.value = ConnectionState.Disconnected   // metrics keep the stale value on purpose
                }
                tick++
                delay(tickMs)
            }
        }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
        _state.value = ConnectionState.Unavailable
    }
}

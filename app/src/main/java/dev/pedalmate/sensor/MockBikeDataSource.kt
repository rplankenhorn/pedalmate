package dev.pedalmate.sensor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Emulator power model: watts scale with resistance times cadence; 40 percent at 80 rpm is 150 W. */
object MockPhysics {
    fun watts(resistance: Int, cadence: Int): Int = (0.046875 * resistance * cadence).roundToInt()
}

/**
 * Emulator stand-in for the Bike+: replays [profile] at [tickMs]; script time counts ticks, not wall time.
 *
 * [setResistance] pins resistance, and with it a flat [FORCED_CADENCE_RPM] cadence and no scripted dropouts, until the
 * next call; the pin survives [stop]/[start]. That keeps the emulator steady enough for the HR controller to hold a band.
 */
class MockBikeDataSource(
    private val profile: ScriptedProfile,
    private val scope: CoroutineScope,
    private val tickMs: Long = 250L,
) : BoundBikeDataSource, BikeResistanceControl {
    private val _metrics = MutableStateFlow(BikeMetrics.ZERO)
    override val metrics: StateFlow<BikeMetrics> = _metrics.asStateFlow()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()
    private var job: Job? = null
    @Volatile private var forcedResistance: Int? = null

    override fun setResistance(percent: Int): Boolean {
        forcedResistance = percent.coerceIn(0, 100)
        return true
    }

    @Synchronized
    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var tick = 0L
            while (isActive) {
                val r = forcedResistance
                val sample = profile.sample((tick * tickMs / 1_000L).toInt())
                if (r != null) {
                    // Forced: flat cadence, and a scripted dropout keeps the last frame instead of going silent.
                    _metrics.value = (sample ?: _metrics.value).copy(
                        resistancePercent = r,
                        cadenceRpm = FORCED_CADENCE_RPM,
                        powerWatts = MockPhysics.watts(r, FORCED_CADENCE_RPM),
                    )
                    _state.value = ConnectionState.Connected
                } else if (sample != null) {
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

    companion object {
        const val FORCED_CADENCE_RPM = 80
    }
}

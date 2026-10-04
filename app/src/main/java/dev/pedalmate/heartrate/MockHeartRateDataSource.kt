package dev.pedalmate.heartrate

import dev.pedalmate.sensor.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Emulator heart-rate curve: 110 bpm plus or minus 20 on a 120 s sine. */
object MockHeartRateProfile {
    fun bpmAt(tSec: Int): Int = 110 + (20.0 * sin(2.0 * PI * tSec / 120.0)).roundToInt()
}

/** Emulator stand-in for a strap: replays [MockHeartRateProfile] every [tickMs]. */
class MockHeartRateDataSource(
    private val scope: CoroutineScope,
    private val tickMs: Long = 1_000L,
) : ManagedHeartRateDataSource {
    private val _bpm = MutableStateFlow<Int?>(null)
    override val bpm: StateFlow<Int?> = _bpm.asStateFlow()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()
    private var job: Job? = null

    @Synchronized
    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var tick = 0L
            while (isActive) {
                _bpm.value = MockHeartRateProfile.bpmAt((tick * tickMs / 1_000L).toInt())
                _state.value = ConnectionState.Connected
                tick++
                delay(tickMs)
            }
        }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
        _bpm.value = null
        _state.value = ConnectionState.Unavailable
    }
}

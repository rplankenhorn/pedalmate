package dev.pedalmate.testutil

import dev.pedalmate.audio.Cue
import dev.pedalmate.audio.CueSink
import dev.pedalmate.heartrate.ManagedHeartRateDataSource
import dev.pedalmate.ride.LiveAggregates
import dev.pedalmate.ride.LiveAggregator
import dev.pedalmate.ride.RideFrame
import dev.pedalmate.ride.RideLog
import dev.pedalmate.sensor.BikeMetrics
import dev.pedalmate.sensor.BoundBikeDataSource
import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.workout.AssetReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeBike : BoundBikeDataSource {
    val metricsFlow = MutableStateFlow(BikeMetrics.ZERO)
    val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val metrics: StateFlow<BikeMetrics> = metricsFlow
    override val connectionState: StateFlow<ConnectionState> = stateFlow
    var started = 0; var stopped = 0
    override fun start() { started++ }
    override fun stop() { stopped++ }
    fun set(power: Int, cadence: Int = 80, resistance: Int = 30, state: ConnectionState = ConnectionState.Connected) {
        metricsFlow.value = BikeMetrics(cadenceRpm = cadence, resistancePercent = resistance, powerWatts = power)
        stateFlow.value = state
    }
}

class FakeHr : ManagedHeartRateDataSource {
    val bpmFlow = MutableStateFlow<Int?>(null)
    val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val bpm: StateFlow<Int?> = bpmFlow
    override val connectionState: StateFlow<ConnectionState> = stateFlow
    var started = 0; var stopped = 0
    override fun start() { started++ }
    override fun stop() { stopped++ }
}

class RecordingLog : RideLog {
    val begins = mutableListOf<Pair<String?, Int?>>()
    val frames = mutableListOf<RideFrame>()
    var finishes = 0
    var failBegin = false
    override suspend fun begin(workoutId: String?, ftp: Int?): Long {
        if (failBegin) throw IllegalStateException("db down")
        begins += workoutId to ftp; return begins.size.toLong()
    }
    override fun offer(frame: RideFrame) { frames += frame }
    override suspend fun finish(): LiveAggregates? { finishes++; return null }
    override fun live(): LiveAggregates? {
        if (begins.isEmpty() || finishes > 0) return null
        return LiveAggregator().also { agg -> frames.forEach(agg::add) }.snapshot()
    }
}

class RecordingCues : CueSink {
    val played = mutableListOf<Cue>()
    override fun play(cue: Cue) { played += cue }
}

class MemAssetReader(private val files: Map<String, String>) : AssetReader {
    override fun list(dir: String) = files.keys.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }
    override fun read(path: String) = files.getValue(path)
}

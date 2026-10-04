package dev.pedalmate.ride

import android.util.Log
import dev.pedalmate.audio.CuePolicy
import dev.pedalmate.audio.CueSink
import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.workout.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface StartResult {
    data object Started : StartResult
    data object UnknownWorkout : StartResult
    data object AlreadyRunning : StartResult

    /** A stop arrived while the start was still suspended; no ride is running. */
    data object Stopped : StartResult
}

/**
 * Composition root of one ride: owns the engine, zone/target logic, cue dispatch and ride log, and
 * publishes a single [snapshot]. Driven by [tick]; [ready] gates a start until persistence is ready.
 */
class RideSession(
    private val hub: SensorHub,
    private val workouts: WorkoutRepository,
    private val rideLog: RideLog,
    private val cues: CueSink,
    private val ftpProvider: () -> Int?,
    private val scope: CoroutineScope,
    private val ready: suspend () -> Unit = {},
) {
    private val _snapshot = MutableStateFlow(RideSnapshot.idle(ftpProvider()))
    val snapshot: StateFlow<RideSnapshot> = _snapshot.asStateFlow()
    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WorkoutEvent> = _events.asSharedFlow()

    private var status = RideStatus.IDLE
    private var starting = false
    private var stopRequested = false   // a stop() that arrived while begin() was suspended
    private var holdsHub = false
    private var engine: WorkoutEngine? = null
    private var workoutName: String? = null
    private var zoneTable: ZoneTable? = null
    private var rideFtp: Int? = null
    private var lastTickMs: Long? = null
    private var freeElapsedMs = 0L
    private var cueJob: Job? = null
    private val smoother = PowerSmoother()
    private val tracker = TargetStatusTracker()

    val isActive: Boolean
        @Synchronized get() = status == RideStatus.RUNNING || status == RideStatus.PAUSED

    suspend fun startWorkout(id: String): StartResult {
        val def = workouts.byId(id) ?: return StartResult.UnknownWorkout
        return begin(def)
    }

    suspend fun startFreeRide(): StartResult = begin(null)

    private suspend fun begin(def: WorkoutDefinition?): StartResult {
        val wasFinished: Boolean
        synchronized(this) {
            if (starting || status == RideStatus.RUNNING || status == RideStatus.PAUSED) return StartResult.AlreadyRunning
            starting = true
            stopRequested = false
            wasFinished = status == RideStatus.FINISHED
        }
        try {
            if (wasFinished) teardown()
            ready()
            val ftp = ftpProvider()
            try {
                rideLog.begin(def?.id, ftp)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "ride log unavailable; riding unrecorded", e)
            }
            hub.acquire()
            val aborted: Boolean
            synchronized(this) {
                aborted = stopRequested
                if (aborted) return@synchronized
                holdsHub = true
                rideFtp = ftp
                zoneTable = ZoneTable.forFtp(ftp)
                workoutName = def?.name
                smoother.reset(); tracker.reset()
                lastTickMs = null; freeElapsedMs = 0L
                cueJob?.cancel(); cueJob = null
                engine = null
                if (def != null) {
                    val e = WorkoutEngine(def, zoneTable)
                    engine = e
                    cueJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        e.events.collect { ev ->
                            _events.tryEmit(ev)
                            CuePolicy.cueFor(ev)?.let { cues.play(it) }
                        }
                    }
                    e.start()
                }
                status = RideStatus.RUNNING
                republish()
            }
            if (aborted) {                            // stop() ran during our suspension: undo what we did
                hub.release()
                safeFinish()
                return StartResult.Stopped
            }
            return StartResult.Started
        } finally {
            synchronized(this) { starting = false; stopRequested = false }
        }
    }

    @Synchronized fun pause() {
        val e = engine ?: return
        if (status != RideStatus.RUNNING) return
        e.pause(); status = RideStatus.PAUSED; republish()
    }

    @Synchronized fun resume() {
        val e = engine ?: return
        if (status != RideStatus.PAUSED) return
        e.resume(); status = RideStatus.RUNNING; republish()
    }

    @Synchronized fun skip() {
        val e = engine ?: return
        if (status == RideStatus.RUNNING || status == RideStatus.PAUSED) { e.skip(); republish() }
    }

    @Synchronized fun tick(nowMs: Long) {
        if (status == RideStatus.IDLE) return
        val last = lastTickMs
        lastTickMs = nowMs
        val delta = if (last == null) 0L else (nowMs - last).coerceAtLeast(0L)
        val e = engine
        if (status == RideStatus.RUNNING && delta > 0) {
            if (e != null) e.advance(delta) else freeElapsedMs += delta
        }
        val live = hub.bike.connectionState.value == ConnectionState.Connected
        if (live) smoother.add(nowMs, hub.bike.metrics.value.powerWatts) else smoother.reset()
        if (status != RideStatus.FINISHED && e?.state?.value?.phase == WorkoutPhase.FINISHED) {
            status = RideStatus.FINISHED
            scope.launch { safeFinish() }
        }
        val snap = buildSnapshot(nowMs)
        if (status == RideStatus.RUNNING) {
            rideLog.offer(RideFrame(snap.powerWatts, snap.cadenceRpm, snap.resistancePercent, snap.heartRateBpm, snap.currentZone?.number))
        }
        _snapshot.value = snap
    }

    suspend fun stop() {
        synchronized(this) { if (starting) stopRequested = true }
        teardown()
    }

    private suspend fun teardown() {
        val needsFinish: Boolean
        val release: Boolean
        synchronized(this) {
            needsFinish = status == RideStatus.RUNNING || status == RideStatus.PAUSED
            release = holdsHub
            holdsHub = false
            cueJob?.cancel(); cueJob = null
            engine = null; zoneTable = null; rideFtp = null; workoutName = null
            status = RideStatus.IDLE; lastTickMs = null; freeElapsedMs = 0L
            smoother.reset(); tracker.reset()
            _snapshot.value = RideSnapshot.idle(ftpProvider())
        }
        if (release) hub.release()
        if (needsFinish) safeFinish()
    }

    private suspend fun safeFinish() {
        // NonCancellable: RideRecorder.finish() is not cancellation-safe; a cancelled caller would leave it begun.
        try { withContext(NonCancellable) { rideLog.finish() } } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(TAG, "ride finish failed", e) }
    }

    private fun republish() { _snapshot.value = buildSnapshot(lastTickMs) }

    private fun buildSnapshot(nowMs: Long?): RideSnapshot {
        val ws = engine?.state?.value
        val bikeState = hub.bike.connectionState.value
        val live = bikeState == ConnectionState.Connected
        val m = hub.bike.metrics.value
        val power = if (live) m.powerWatts else null
        val smoothed = if (live && nowMs != null) smoother.value(nowMs) else null
        val table = zoneTable
        val currentZone = if (table != null && smoothed != null) table.zoneFor(smoothed) else null
        val step = ws?.currentStep
        val range = step?.wattRange
        val targetStatus = if (nowMs != null) tracker.update(nowMs, smoothed, range) else null
        val hrState = hub.hr.connectionState.value
        return RideSnapshot(
            status = status, workoutName = workoutName, bikeState = bikeState, hrState = hrState,
            powerWatts = power, smoothedPowerWatts = smoothed,
            cadenceRpm = if (live) m.cadenceRpm else null,
            resistancePercent = if (live) m.resistancePercent else null,
            heartRateBpm = if (hrState == ConnectionState.Connected) hub.hr.bpm.value else null,
            ftp = rideFtp, currentZone = currentZone, targetZone = step?.zone, targetRange = range,
            targetStatus = targetStatus, workout = ws, elapsedMs = ws?.totalElapsedMs ?: freeElapsedMs,
        )
    }

    private companion object { const val TAG = "PedalMate" }
}

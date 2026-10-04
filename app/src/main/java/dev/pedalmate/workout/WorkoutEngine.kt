package dev.pedalmate.workout

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle phase of a [WorkoutEngine]. */
enum class WorkoutPhase { IDLE, RUNNING, PAUSED, FINISHED }

/** Display-ready description of one workout step; [wattRange] is null without a zone or FTP. */
data class StepInfo(val index: Int, val label: String, val zone: PowerZone?, val wattRange: ZoneRange?, val seconds: Int)

/** Immutable snapshot of workout progress. */
data class WorkoutState(
    val phase: WorkoutPhase, val stepIndex: Int, val stepCount: Int,
    val stepElapsedMs: Long, val stepRemainingMs: Long,
    val totalElapsedMs: Long, val totalRemainingMs: Long, val totalMs: Long,
    val progress: Float, val currentStep: StepInfo?, val nextStep: StepInfo?,
)

/** One-shot events emitted by the engine as it steps through a workout. */
sealed interface WorkoutEvent {
    data class Started(val first: StepInfo) : WorkoutEvent
    data class IntervalChanged(val from: StepInfo, val to: StepInfo) : WorkoutEvent
    /** Final-seconds beep: [seconds] is 3, 2 or 1. */
    data class Countdown(val seconds: Int) : WorkoutEvent
    data class Skipped(val fromIndex: Int) : WorkoutEvent
    data object Finished : WorkoutEvent
}

/** Pure-Kotlin interval engine driven by [advance]; has no wall clock and no Android dependencies. */

class WorkoutEngine(definition: WorkoutDefinition, zoneTable: ZoneTable?) {
    init { require(definition.steps.isNotEmpty()) { "workout needs at least one step" } }

    private val steps: List<StepInfo> = definition.steps.mapIndexed { i, s ->
        val zone = s.zone?.let { PowerZone.fromNumber(it) }
        StepInfo(i, s.label, zone, if (zone != null && zoneTable != null) zoneTable.rangeOf(zone) else null, s.seconds)
    }
    private val stepMs: List<Long> = steps.map { it.seconds * 1_000L }
    private val startsAtMs: List<Long> = stepMs.runningFold(0L) { a, b -> a + b }.dropLast(1)
    private val totalMs: Long = stepMs.sum()
    private val last = steps.lastIndex

    private var phase = WorkoutPhase.IDLE
    private var index = 0
    private var elapsedInStep = 0L

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<WorkoutState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<WorkoutEvent> = _events.asSharedFlow()

    @Synchronized fun start() {
        if (phase != WorkoutPhase.IDLE) return
        phase = WorkoutPhase.RUNNING
        _events.tryEmit(WorkoutEvent.Started(steps[0]))
        publish()
    }

    @Synchronized fun pause() { if (phase == WorkoutPhase.RUNNING) { phase = WorkoutPhase.PAUSED; publish() } }
    @Synchronized fun resume() { if (phase == WorkoutPhase.PAUSED) { phase = WorkoutPhase.RUNNING; publish() } }

    @Synchronized fun skip() {
        if (phase != WorkoutPhase.RUNNING && phase != WorkoutPhase.PAUSED) return
        _events.tryEmit(WorkoutEvent.Skipped(index))
        if (index == last) finish() else moveToNext()
        publish()
    }

    @Synchronized fun advance(deltaMs: Long) {
        if (phase != WorkoutPhase.RUNNING || deltaMs <= 0) return
        var left = deltaMs
        var crossed = false
        while (left > 0 && phase == WorkoutPhase.RUNNING) {
            val remaining = stepMs[index] - elapsedInStep
            if (left < remaining) {
                elapsedInStep += left
                if (!crossed) maybeCountdown(before = remaining, after = remaining - left)
                left = 0
            } else {
                left -= remaining
                crossed = true
                if (index == last) finish() else moveToNext()
            }
        }
        publish()
    }

    private fun moveToNext() {
        val from = steps[index]
        index++
        elapsedInStep = 0
        _events.tryEmit(WorkoutEvent.IntervalChanged(from, steps[index]))
    }

    private fun finish() {
        phase = WorkoutPhase.FINISHED
        _events.tryEmit(WorkoutEvent.Finished)
    }

    private fun maybeCountdown(before: Long, after: Long) {
        if (steps[index].seconds < 4 || index == last) return
        for (n in 1..3) {
            if (before > n * 1_000L && after <= n * 1_000L) { _events.tryEmit(WorkoutEvent.Countdown(n)); return }
        }
    }

    private fun publish() { _state.value = snapshot() }

    private fun snapshot(): WorkoutState = when (phase) {
        WorkoutPhase.IDLE -> WorkoutState(phase, 0, steps.size, 0, stepMs[0], 0, totalMs, totalMs, 0f, null, steps[0])
        WorkoutPhase.FINISHED -> WorkoutState(phase, last, steps.size, stepMs[last], 0, totalMs, 0, totalMs, 1f, null, null)
        else -> {
            val total = startsAtMs[index] + elapsedInStep
            WorkoutState(
                phase, index, steps.size, elapsedInStep, stepMs[index] - elapsedInStep,
                total, totalMs - total, totalMs, total.toFloat() / totalMs, steps[index], steps.getOrNull(index + 1),
            )
        }
    }
}

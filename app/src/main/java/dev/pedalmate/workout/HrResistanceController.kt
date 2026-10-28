package dev.pedalmate.workout

import kotlin.math.abs

data class StepRef(val index: Int, val hrTarget: Boolean, val resistance: Int?)

sealed interface ResistanceMode {
    data object AUTO : ResistanceMode
    data object HOLD_NO_HR : ResistanceMode
    data object HOLD_NO_CADENCE : ResistanceMode
    data object HOLD_NO_BIKE : ResistanceMode

    /** Ride paused, finished, or the step is neither hrTarget nor resistance. */
    data object PAUSED : ResistanceMode
    data class MANUAL(val graceRemainingMs: Long) : ResistanceMode
}

data class ControlInput(
    val nowMs: Long,
    val running: Boolean,
    val step: StepRef?,
    val band: IntRange,
    val hrBpm: Int?,
    val hrAgeMs: Long?,
    val cadenceRpm: Int?,
    val observedResistance: Int?,
)

/**
 * Clockless state machine that nudges bike resistance to keep heart rate inside a band.
 * Time only enters through [ControlInput.nowMs]. Not thread-safe: the caller serialises access.
 */
class HrResistanceController {
    var mode: ResistanceMode = ResistanceMode.PAUSED
        private set

    private var started = false
    private var stepIndex: Int? = null

    // Per-step state.
    private var baseline: Int? = null
    private var commanded: Int? = null
    private var pendingEntry: Int? = null
    private var lastAdjustAt = 0L
    private var settleUntil = 0L
    private var entryRetryAt = 0L
    private var graceUntil: Long? = null
    private var lastManualObserved: Int? = null

    // Cross-step state.
    private var holdSince: Long? = null

    // In-flight command bookkeeping.
    private var inFlight = false
    private var inFlightValue = 0
    private var inFlightAt = 0L
    private var inFlightIsEntry = false
    private var inFlightStale = false

    /** One tick. Returns a resistance to command, or null. The caller must answer every non-null return with [onCommandResult]. */
    fun update(input: ControlInput): Int? {
        val now = input.nowMs
        val step = input.step
        if (!started || step?.index != stepIndex) startStep(step, now)

        val observed = input.observedResistance
        val live = input.running && step != null && (step.hrTarget || step.resistance != null)
        if (live && observed != null) trackKnob(observed, now, step!!.hrTarget)

        mode = computeMode(input, live)
        trackHold(now)

        if (inFlight) return null
        if (live && observed != null) {
            entryCommand(now)?.let { return startCommand(it, now, entry = true) }
        }
        if (mode == ResistanceMode.AUTO && pendingEntry == null) {
            autoCommand(input, now)?.let { return startCommand(it, now, entry = false) }
        }
        return null
    }

    fun onCommandResult(delivered: Boolean) {
        if (!inFlight) return
        inFlight = false
        if (inFlightStale) {
            // The step changed while the command was out. If it landed, the knob will physically
            // move to that value inside the new step, so it must not read as a manual turn.
            if (delivered) {
                commanded = inFlightValue
                settleUntil = inFlightAt + SETTLE_MS
            }
            return
        }
        if (delivered) {
            commanded = inFlightValue
            settleUntil = inFlightAt + SETTLE_MS
            if (inFlightIsEntry) pendingEntry = null else lastAdjustAt = inFlightAt
        } else if (inFlightIsEntry) {
            entryRetryAt = inFlightAt + RETRY_MS
        } else {
            // Rate-limit retries to one attempt per interval; baseline and settle stay untouched.
            lastAdjustAt = inFlightAt
        }
    }

    fun reset() {
        started = false
        stepIndex = null
        mode = ResistanceMode.PAUSED
        holdSince = null
        inFlight = false
        inFlightStale = false
        clearStepState()
    }

    private fun clearStepState() {
        baseline = null
        commanded = null
        pendingEntry = null
        lastAdjustAt = 0L
        settleUntil = 0L
        entryRetryAt = 0L
        graceUntil = null
        lastManualObserved = null
    }

    private fun startStep(step: StepRef?, now: Long) {
        started = true
        stepIndex = step?.index
        clearStepState()
        pendingEntry = step?.resistance
        lastAdjustAt = now
        if (inFlight) inFlightStale = true
    }

    /** Baseline capture, knob-override detection and grace expiry. */
    private fun trackKnob(observed: Int, now: Long, hrTarget: Boolean) {
        if (baseline == null && commanded == null) baseline = observed
        if (!hrTarget || inFlight) return
        val grace = graceUntil
        if (grace != null) {
            if (now >= grace) {
                baseline = observed
                commanded = null
                lastAdjustAt = now
                graceUntil = null
                lastManualObserved = null
            } else if (abs(observed - (lastManualObserved ?: observed)) >= 1) {
                graceUntil = now + GRACE_MS
                lastManualObserved = observed
            }
        } else if (now >= settleUntil) {
            val reference = commanded ?: baseline ?: return
            if (abs(observed - reference) >= OVERRIDE_DELTA) {
                graceUntil = now + GRACE_MS
                lastManualObserved = observed
            }
        }
    }

    private fun computeMode(input: ControlInput, live: Boolean): ResistanceMode {
        val step = input.step
        if (!live || step == null) return ResistanceMode.PAUSED
        if (input.observedResistance == null) return ResistanceMode.HOLD_NO_BIKE
        // A resistance-only step has nothing to show, before or after its entry command.
        if (!step.hrTarget) return ResistanceMode.PAUSED
        val age = input.hrAgeMs
        if (input.hrBpm == null || age == null || age > HR_MAX_AGE_MS) return ResistanceMode.HOLD_NO_HR
        val cadence = input.cadenceRpm
        if (cadence == null || cadence < CADENCE_FLOOR) return ResistanceMode.HOLD_NO_CADENCE
        val grace = graceUntil
        if (grace != null) return ResistanceMode.MANUAL((grace - input.nowMs).coerceAtLeast(0L))
        return ResistanceMode.AUTO
    }

    /** A long enough hold (no HR, no pedalling, no bike, paused) restarts the adjust timer. */
    private fun trackHold(now: Long) {
        val holding = mode != ResistanceMode.AUTO && mode !is ResistanceMode.MANUAL
        if (!holding) {
            holdSince = null
            return
        }
        val since = holdSince ?: now.also { holdSince = it }
        if (now - since >= HOLD_RESET_MS) lastAdjustAt = now
    }

    /** Entry commands skip the HR and cadence rules and the clamps, but still need the bike. */
    private fun entryCommand(now: Long): Int? =
        pendingEntry?.takeIf { now >= entryRetryAt }

    private fun autoCommand(input: ControlInput, now: Long): Int? {
        if (now - lastAdjustAt < ADJUST_INTERVAL_MS || now < settleUntil) return null
        val hr = input.hrBpm ?: return null
        val reference = commanded ?: baseline ?: return null
        val direction: Int
        val error: Int
        when {
            hr < input.band.first -> { direction = 1; error = input.band.first - hr }
            hr > input.band.last -> { direction = -1; error = hr - input.band.last }
            else -> return null
        }
        val unit = if (error >= BIG_ERROR_BPM) 2 else 1
        val target = (reference + direction * unit).coerceIn(AUTO_MIN, AUTO_MAX)
        // Never move against the error (e.g. reference above AUTO_MAX while HR is low).
        if ((target - reference) * direction <= 0) return null
        return target
    }

    private fun startCommand(value: Int, now: Long, entry: Boolean): Int {
        inFlight = true
        inFlightStale = false
        inFlightValue = value
        inFlightAt = now
        inFlightIsEntry = entry
        return value
    }

    companion object {
        const val ADJUST_INTERVAL_MS = 20_000L
        const val SETTLE_MS = 3_000L
        const val HR_MAX_AGE_MS = 30_000L
        const val HOLD_RESET_MS = 10_000L
        const val GRACE_MS = 75_000L
        const val CADENCE_FLOOR = 30
        const val OVERRIDE_DELTA = 2
        const val AUTO_MIN = 10
        const val AUTO_MAX = 70
        const val BIG_ERROR_BPM = 10
        const val RETRY_MS = 2_000L
    }
}

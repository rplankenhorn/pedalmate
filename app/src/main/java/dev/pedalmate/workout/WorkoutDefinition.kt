package dev.pedalmate.workout

import kotlinx.serialization.Serializable

/**
 * One timed step of a workout. A null [zone] means no power target. At most one of [zone], [hrTarget]
 * and [resistance] may be set: [hrTarget] holds the heart-rate target, [resistance] pins the bike (0..100).
 */
@Serializable
data class WorkoutStep(
    val label: String,
    val seconds: Int,
    val zone: Int? = null,
    val tag: String? = null,
    val hrTarget: Boolean = false,
    val resistance: Int? = null,
)

/** A workout: an ordered list of timed steps with optional power-zone targets. */
@Serializable
data class WorkoutDefinition(
    val id: String,
    val name: String,
    val description: String? = null,
    val steps: List<WorkoutStep>,
) {
    val totalSeconds: Int get() = steps.sumOf { it.seconds }

    /** True when any step drives bike resistance (an HR target or a fixed resistance). */
    val usesResistanceControl: Boolean get() = steps.any { it.hrTarget || it.resistance != null }

    /** Returns every validation error as a readable message; empty when valid. */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (!ID_REGEX.matches(id)) errors += "id '$id' must match ${ID_REGEX.pattern}"
        if (name.isBlank()) errors += "name must not be blank"
        if (name.length > 60) errors += "name must be at most 60 characters"
        if (steps.isEmpty() || steps.size > 200) errors += "steps must have 1..200 entries (was ${steps.size})"
        steps.forEachIndexed { i, s ->
            val n = i + 1
            if (s.label.isBlank()) errors += "step $n: label must not be blank"
            if (s.label.length > 40) errors += "step $n: label must be at most 40 characters"
            if (s.seconds !in 1..21600) errors += "step $n: seconds must be 1..21600 (was ${s.seconds})"
            if (s.zone != null && s.zone !in 1..7) errors += "step $n: zone must be 1..7 or omitted (was ${s.zone})"
            if (listOf(s.zone != null, s.hrTarget, s.resistance != null).count { it } > 1) {
                errors += "step $n: at most one of zone, hrTarget, resistance"
            }
            if (s.resistance != null && s.resistance !in 0..100) {
                errors += "step $n: resistance must be 0..100 (was ${s.resistance})"
            }
            if (s.tag != null && (s.tag.isBlank() || s.tag.length > 20)) {
                errors += "step $n: tag must be non-blank and at most 20 characters"
            }
        }
        return errors
    }
}

private val ID_REGEX = Regex("^[a-z0-9][a-z0-9-]{0,39}$")

/** Thrown when workout JSON cannot be parsed or fails validation. */
class WorkoutFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

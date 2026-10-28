package dev.pedalmate.ui.setup

/** Result of parsing the heart-rate target band text fields. */
sealed interface HrTargetParse {
    data class Valid(val min: Int, val max: Int) : HrTargetParse
    data class Invalid(val reason: String) : HrTargetParse
}

/** Pure validation of the HR target band: whole bpm, each 60 to 200, max at least 5 above min. */
object HrTargetInput {
    const val RANGE_ERROR = "Heart rate must be 60 to 200 bpm"
    const val ORDER_ERROR = "Max must be at least 5 above min"
    const val FORMAT_ERROR = "Enter whole bpm"
    private const val MIN_BPM = 60
    private const val MAX_BPM = 200
    private const val MIN_GAP = 5
    private val digits = Regex("^[0-9]+$")

    fun isValid(min: Int, max: Int): Boolean =
        min in MIN_BPM..MAX_BPM && max in MIN_BPM..MAX_BPM && max - min >= MIN_GAP

    fun parse(minText: String, maxText: String): HrTargetParse {
        val a = minText.trim()
        val b = maxText.trim()
        if (!digits.matches(a) || !digits.matches(b)) return HrTargetParse.Invalid(FORMAT_ERROR)
        // More than 4 digits cannot be in range (and could overflow toInt).
        if (a.length > 4 || b.length > 4) return HrTargetParse.Invalid(RANGE_ERROR)
        val min = a.toInt()
        val max = b.toInt()
        if (min !in MIN_BPM..MAX_BPM || max !in MIN_BPM..MAX_BPM) return HrTargetParse.Invalid(RANGE_ERROR)
        if (max - min < MIN_GAP) return HrTargetParse.Invalid(ORDER_ERROR)
        return HrTargetParse.Valid(min, max)
    }
}

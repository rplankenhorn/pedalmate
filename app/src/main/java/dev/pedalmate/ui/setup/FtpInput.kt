package dev.pedalmate.ui.setup

/** Result of parsing the FTP text field. */
sealed interface FtpParse {
    data class Valid(val ftp: Int) : FtpParse
    data object Empty : FtpParse
    data class Invalid(val reason: String) : FtpParse
}

/** Pure validation of the FTP text field: whole watts, 50 to 600; blank means "no FTP". */
object FtpInput {
    const val RANGE_ERROR = "FTP must be 50 to 600 W"
    const val FORMAT_ERROR = "Enter whole watts"
    private val digits = Regex("^[0-9]+$")

    fun parse(text: String): FtpParse {
        val t = text.trim()
        if (t.isEmpty()) return FtpParse.Empty
        if (!digits.matches(t)) return FtpParse.Invalid(FORMAT_ERROR)
        if (t.length > 4) return FtpParse.Invalid(RANGE_ERROR)
        val v = t.toInt()
        return if (v in 50..600) FtpParse.Valid(v) else FtpParse.Invalid(RANGE_ERROR)
    }
}

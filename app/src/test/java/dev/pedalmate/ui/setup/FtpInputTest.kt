package dev.pedalmate.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class FtpInputTest {
    private val range = FtpParse.Invalid(FtpInput.RANGE_ERROR)
    private val format = FtpParse.Invalid(FtpInput.FORMAT_ERROR)

    @Test
    fun `table of inputs`() {
        val cases = listOf(
            "200" to FtpParse.Valid(200), " 200 " to FtpParse.Valid(200), "0200" to FtpParse.Valid(200),
            "50" to FtpParse.Valid(50), "600" to FtpParse.Valid(600),
            "49" to range, "601" to range, "0" to range, "10000" to range, "99999999999" to range,
            "" to FtpParse.Empty, "   " to FtpParse.Empty,
            "abc" to format, "20.5" to format, "-100" to format, "2 0" to format,
            "\u0662\u0660\u0660" to format,
        )
        for ((text, expected) in cases) assertEquals("input '$text'", expected, FtpInput.parse(text))
    }
}

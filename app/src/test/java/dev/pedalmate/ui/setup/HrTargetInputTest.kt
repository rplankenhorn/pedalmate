package dev.pedalmate.ui.setup

import org.junit.Assert.*
import org.junit.Test

class HrTargetInputTest {
    @Test fun `valid pair`() = assertEquals(HrTargetParse.Valid(130, 140), HrTargetInput.parse("130", "140"))
    @Test fun `trims whitespace`() = assertEquals(HrTargetParse.Valid(130, 140), HrTargetInput.parse(" 130 ", "140 "))
    @Test fun `below range`() = assertEquals(HrTargetParse.Invalid(HrTargetInput.RANGE_ERROR), HrTargetInput.parse("59", "140"))
    @Test fun `above range`() = assertEquals(HrTargetParse.Invalid(HrTargetInput.RANGE_ERROR), HrTargetInput.parse("130", "201"))
    @Test fun `huge number is range not crash`() =
        assertEquals(HrTargetParse.Invalid(HrTargetInput.RANGE_ERROR), HrTargetInput.parse("130", "99999999999"))
    @Test fun `too close`() = assertEquals(HrTargetParse.Invalid(HrTargetInput.ORDER_ERROR), HrTargetInput.parse("130", "134"))
    @Test fun `exactly five apart ok`() = assertEquals(HrTargetParse.Valid(130, 135), HrTargetInput.parse("130", "135"))
    @Test fun `non digits`() = assertEquals(HrTargetParse.Invalid(HrTargetInput.FORMAT_ERROR), HrTargetInput.parse("abc", "140"))
    @Test fun `blank min`() = assertEquals(HrTargetParse.Invalid(HrTargetInput.FORMAT_ERROR), HrTargetInput.parse("", "140"))
    @Test fun `blank max`() = assertEquals(HrTargetParse.Invalid(HrTargetInput.FORMAT_ERROR), HrTargetInput.parse("130", "  "))
    @Test fun `format beats range`() = assertEquals(HrTargetParse.Invalid(HrTargetInput.FORMAT_ERROR), HrTargetInput.parse("10", "x"))
    @Test fun `isValid bounds`() {
        assertTrue(HrTargetInput.isValid(60, 65)); assertTrue(HrTargetInput.isValid(195, 200)); assertFalse(HrTargetInput.isValid(196, 200))
        assertFalse(HrTargetInput.isValid(130, 130)); assertFalse(HrTargetInput.isValid(59, 100)); assertFalse(HrTargetInput.isValid(150, 140))
    }
}

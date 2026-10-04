package dev.pedalmate.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpCalculatorTest {
    @Test fun `95 percent of the 20 minute average rounds half up`() {
        assertEquals(238, FtpCalculator.fromTwentyMinuteAverage(250.0))
        assertEquals(190, FtpCalculator.fromTwentyMinuteAverage(200.0))
        assertEquals(240, FtpCalculator.fromTwentyMinuteAverage(253.0))
        assertEquals(241, FtpCalculator.fromTwentyMinuteAverage(253.2))
        assertEquals(95, FtpCalculator.fromTwentyMinuteAverage(100.0))
        assertEquals(100, FtpCalculator.fromTwentyMinuteAverage(105.0))
        assertEquals(10, FtpCalculator.fromTwentyMinuteAverage(10.0))
        assertEquals(0, FtpCalculator.fromTwentyMinuteAverage(0.0))
    }

    @Test fun `valid FTP is 50 to 600 inclusive`() {
        assertFalse(FtpCalculator.isValid(null))
        assertFalse(FtpCalculator.isValid(49))
        assertTrue(FtpCalculator.isValid(50))
        assertTrue(FtpCalculator.isValid(600))
        assertFalse(FtpCalculator.isValid(601))
    }
}

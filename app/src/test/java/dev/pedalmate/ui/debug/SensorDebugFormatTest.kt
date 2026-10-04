package dev.pedalmate.ui.debug

import dev.pedalmate.sensor.BikeMetrics
import dev.pedalmate.sensor.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorDebugFormatTest {
    private val m = BikeMetrics(cadenceRpm = 87, resistancePercent = 44, powerWatts = 183)

    @Test fun `connected shows the three values`() {
        assertEquals(DebugText("183 W", "87", "44", null), debugText(m, ConnectionState.Connected))
    }

    @Test fun `unavailable shows dashes and NO SENSOR, never the last watts`() {
        assertEquals(DebugText("--", "--", "--", "NO SENSOR"), debugText(m, ConnectionState.Unavailable))
    }

    @Test fun `disconnected shows dashes and SENSOR LOST`() {
        assertEquals(DebugText("--", "--", "--", "SENSOR LOST"), debugText(m, ConnectionState.Disconnected))
    }
}

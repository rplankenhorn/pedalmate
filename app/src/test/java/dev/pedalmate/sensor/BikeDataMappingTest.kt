package dev.pedalmate.sensor

import com.onepeloton.affernetservice.BikeData
import org.junit.Assert.assertEquals
import org.junit.Test

class BikeDataMappingTest {
    private fun frame(rpm: Long = 0, power: Long = 0, res: Int = 0) = BikeData().apply {
        this.rpm = rpm; this.power = power; this.currentResistance = res
    }

    @Test fun `power is centi-watts divided by 100`() {
        assertEquals(183, frame(power = 18_300).toBikeMetrics().powerWatts)
        assertEquals(183, frame(power = 18_399).toBikeMetrics().powerWatts)   // truncates
        assertEquals(0, frame(power = 99).toBikeMetrics().powerWatts)
        assertEquals(0, frame(power = 0).toBikeMetrics().powerWatts)
    }

    @Test fun `cadence maps straight through`() {
        assertEquals(87, frame(rpm = 87).toBikeMetrics().cadenceRpm)
    }

    @Test fun `resistance is clamped to 0 through 100`() {
        assertEquals(44, frame(res = 44).toBikeMetrics().resistancePercent)
        assertEquals(100, frame(res = 101).toBikeMetrics().resistancePercent)
        assertEquals(0, frame(res = -5).toBikeMetrics().resistancePercent)
    }

    @Test fun `defensive clamps keep garbage frames in range`() {
        assertEquals(0, frame(power = -500).toBikeMetrics().powerWatts)
        assertEquals(9_999, frame(power = 1_000_000).toBikeMetrics().powerWatts)
        assertEquals(0, frame(rpm = -3).toBikeMetrics().cadenceRpm)
        assertEquals(999, frame(rpm = 5_000).toBikeMetrics().cadenceRpm)
    }
}

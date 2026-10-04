package dev.pedalmate

import dev.pedalmate.sensor.SensorFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldSmokeRealTest {
    @Test
    fun `real flavor source set is the one on the classpath`() {
        assertEquals("real", SensorFactory.FLAVOR)
    }
}

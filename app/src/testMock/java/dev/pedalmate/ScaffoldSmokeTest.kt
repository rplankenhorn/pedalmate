package dev.pedalmate

import dev.pedalmate.sensor.SensorFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldSmokeTest {
    @Test
    fun `mock flavor source set is the one on the classpath`() {
        assertEquals("mock", SensorFactory.FLAVOR)
    }
}

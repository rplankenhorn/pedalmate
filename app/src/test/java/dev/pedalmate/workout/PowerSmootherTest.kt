package dev.pedalmate.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerSmootherTest {
    @Test fun `empty smoother has no value`() = assertNull(PowerSmoother().value(0))

    @Test fun `mean over the last 3 s window`() {
        val s = PowerSmoother()
        s.add(0, 100); s.add(1_000, 200); s.add(2_000, 300)
        assertEquals(200, s.value(2_000))
        s.add(3_000, 400)                     // sample at t=0 leaves the window (now - 3000, now]
        assertEquals(300, s.value(3_000))     // mean of 200, 300, 400
    }

    @Test fun `old samples expire and an empty window is null again`() {
        val s = PowerSmoother()
        s.add(3_000, 400)
        assertEquals(400, s.value(5_001))
        assertNull(s.value(6_000))
    }

    @Test fun `mean rounds half up`() {
        val s = PowerSmoother(); s.add(0, 100); s.add(500, 101)
        assertEquals(101, s.value(500))
    }

    @Test fun `reset clears`() {
        val s = PowerSmoother(); s.add(0, 100); s.reset(); assertNull(s.value(0))
    }
}

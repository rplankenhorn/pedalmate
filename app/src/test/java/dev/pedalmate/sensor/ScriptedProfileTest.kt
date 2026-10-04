package dev.pedalmate.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScriptedProfileTest {
    @Test fun `same seed gives identical samples regardless of call order`() {
        val a = ScriptedProfile(seed = 7)
        val b = ScriptedProfile(seed = 7)
        val forward = (0..300).map { a.sample(it) }
        val backward = (300 downTo 0).map { b.sample(it) }.reversed()
        assertEquals(forward, backward)
    }

    @Test fun `different seeds differ somewhere`() {
        val a = (0..139).map { ScriptedProfile(seed = 1).sample(it) }
        val b = (0..139).map { ScriptedProfile(seed = 2).sample(it) }
        assertNotEquals(a, b)
    }

    @Test fun `each 20 s block sits at its zone fraction of FTP within noise`() {
        val fractions = doubleArrayOf(0.40, 0.65, 0.82, 0.97, 1.12, 1.35, 1.70)
        val p = ScriptedProfile(seed = 3, ftp = 200)
        for (z in 0..6) {
            val m = p.sample(z * 20 + 10)!!
            val centre = Math.round(200 * fractions[z]).toInt()
            assertTrue("zone block $z watts=${m.powerWatts}", abs(m.powerWatts - centre) <= 5)
        }
    }

    @Test fun `six second dropout after the ramp then it resumes`() {
        val p = ScriptedProfile(seed = 1)
        assertNotNull(p.sample(139))
        for (t in 140..145) assertNull("t=$t", p.sample(t))
        assertNotNull(p.sample(146))
        assertNull(p.sample(146 + 140))
    }

    @Test fun `dropouts can be disabled`() {
        val p = ScriptedProfile(seed = 1, dropouts = false)
        assertTrue((0..600).all { p.sample(it) != null })
    }

    @Test fun `cadence and resistance stay in range`() {
        val p = ScriptedProfile(seed = 5)
        (0..600).mapNotNull { p.sample(it) }.forEach {
            assertTrue(it.cadenceRpm in 0..150)
            assertTrue(it.resistancePercent in 0..100)
            assertTrue(it.powerWatts >= 0)
        }
    }
}

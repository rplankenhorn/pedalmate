package dev.pedalmate.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HrResistanceControllerTest {
    private var c = HrResistanceController()
    private var obs: Int? = 40
    private val hrStep = StepRef(1, true, null)

    private fun tick(
        t: Long,
        hr: Int? = 120,
        age: Long? = 0,
        cad: Int? = 80,
        res: Int? = obs,
        running: Boolean = true,
        step: StepRef? = hrStep,
        band: IntRange = 130..140,
    ): Int? = c.update(ControlInput(t, running, step, band, hr, age, cad, res))

    /** Delivers [v] and feeds it back as the observed resistance, like a real bike would. */
    private fun deliver(v: Int?) {
        if (v != null) {
            c.onCommandResult(true)
            obs = v
        }
    }

    /** Ticks every 250 ms over [from]..[to] inclusive; delivers and records every command. */
    private fun run(
        from: Long,
        to: Long,
        hr: Int? = 120,
        age: Long? = 0,
        cad: Int? = 80,
        running: Boolean = true,
        step: StepRef? = hrStep,
        band: IntRange = 130..140,
    ): List<Pair<Long, Int>> {
        val out = mutableListOf<Pair<Long, Int>>()
        var t = from
        while (t <= to) {
            val r = tick(t, hr, age, cad, obs, running, step, band)
            if (r != null) {
                out += t to r
                deliver(r)
            }
            t += 250
        }
        return out
    }

    @Test fun `inside the band nothing is commanded`() {
        assertTrue(run(0, 120_000, hr = 135).isEmpty())
        assertEquals(ResistanceMode.AUTO, c.mode)
    }

    @Test fun `below the band adds one after the first interval then waits 20 s`() {
        assertTrue(run(0, 19_750, hr = 125).isEmpty())
        assertEquals(listOf(20_000L to 41), run(20_000, 39_750, hr = 125))
        assertEquals(listOf(40_000L to 42), run(40_000, 59_750, hr = 125))
    }

    @Test fun `a big error adds two`() {
        assertEquals(listOf(20_000L to 42), run(0, 39_750, hr = 115).take(1))
    }

    @Test fun `above the band subtracts`() {
        assertEquals(20_000L to 39, run(0, 20_000, hr = 146).single())
        c = HrResistanceController(); obs = 40
        assertEquals(20_000L to 38, run(0, 20_000, hr = 155).single())
    }

    @Test fun `clamps at 10 and 70`() {
        obs = 70
        assertTrue(run(0, 200_000, hr = 100).isEmpty())
        c = HrResistanceController(); obs = 10
        assertTrue(run(0, 200_000, hr = 180).isEmpty())
    }

    private fun warmThenAt20s(hr: Int? = 125, age: Long? = 0, cad: Int? = 80, res: Int? = 40): Int? {
        c = HrResistanceController(); obs = 40
        tick(0, hr = 125)
        return tick(20_000, hr = hr, age = age, cad = cad, res = res)
    }

    @Test fun `each hold reason suppresses commands`() {
        assertNull(warmThenAt20s(res = null)); assertEquals(ResistanceMode.HOLD_NO_BIKE, c.mode)
        assertNull(warmThenAt20s(hr = null)); assertEquals(ResistanceMode.HOLD_NO_HR, c.mode)
        assertNull(warmThenAt20s(age = 30_001)); assertEquals(ResistanceMode.HOLD_NO_HR, c.mode)
        assertEquals(41, warmThenAt20s(age = 25_000))
        assertNull(warmThenAt20s(cad = 29)); assertEquals(ResistanceMode.HOLD_NO_CADENCE, c.mode)
        assertEquals(41, warmThenAt20s(cad = 30))
        assertNull(warmThenAt20s(cad = null)); assertEquals(ResistanceMode.HOLD_NO_CADENCE, c.mode)
    }

    @Test fun `a 5 s hold leaves the adjust timer alone`() {
        assertTrue(run(0, 9_750, hr = 125).isEmpty())
        assertTrue(run(10_000, 14_750, hr = 125, cad = 20).isEmpty())
        assertEquals(20_000L to 41, run(15_000, 20_000, hr = 125).single())
    }

    @Test fun `a hold of 10 s or more resets the adjust timer`() {
        assertTrue(run(0, 4_750, hr = 125).isEmpty())
        assertTrue(run(5_000, 15_000, hr = 125, cad = 20).isEmpty())
        assertTrue(run(15_250, 20_000, hr = 125).isEmpty())
        assertEquals(35_000L to 41, run(20_250, 35_000, hr = 125).single())
    }

    @Test fun `a pause of 30 s resets the adjust timer`() {
        assertTrue(run(0, 4_750, hr = 125).isEmpty())
        assertTrue(run(5_000, 35_000, hr = 125, running = false).isEmpty())
        assertEquals(ResistanceMode.PAUSED, c.mode)
        assertEquals(55_000L to 41, run(35_250, 55_000, hr = 125).single())
    }

    @Test fun `nothing is commanded while a command is in flight`() {
        for (t in 0L until 20_000L step 250) assertNull(tick(t, hr = 125))
        assertEquals(41, tick(20_000, hr = 125))
        var t = 20_250L
        while (t <= 60_000L) { assertNull(tick(t, hr = 125)); t += 250 }
        c.onCommandResult(true)
        obs = 41
        // timer was restarted by the delivery at the time the command went out (t=20_000)
        assertEquals(42, tick(60_250, hr = 125))
    }

    /** Controller already commanded 41 at t=20_000 and the bike confirms it. */
    private fun commanded41() {
        assertTrue(run(0, 19_750, hr = 125).isEmpty())
        assertEquals(20_000L to 41, run(20_000, 20_000, hr = 125).single())
    }

    @Test fun `manual override is judged only after the settle window`() {
        commanded41()
        assertNull(tick(21_000, hr = 125, res = 46))
        assertEquals(ResistanceMode.AUTO, c.mode)
        assertNull(tick(23_000, hr = 125, res = 46))
        assertEquals(ResistanceMode.MANUAL(75_000), c.mode)
    }

    @Test fun `grace restarts on further knob movement`() {
        commanded41()
        tick(23_000, hr = 125, res = 46)
        assertEquals(ResistanceMode.MANUAL(75_000), c.mode)
        tick(40_000, hr = 125, res = 46)
        assertEquals(ResistanceMode.MANUAL(58_000), c.mode)
        tick(60_000, hr = 125, res = 48)
        assertEquals(ResistanceMode.MANUAL(75_000), c.mode)
        tick(134_750, hr = 125, res = 48)
        assertEquals(ResistanceMode.MANUAL(250), c.mode)
    }

    @Test fun `after grace the knob position is the baseline and the timer restarts`() {
        commanded41()
        tick(23_000, hr = 125, res = 46)
        tick(60_000, hr = 125, res = 48)
        assertNull(tick(135_000, hr = 125, res = 48))
        assertEquals(ResistanceMode.AUTO, c.mode)
        for (t in 135_250L until 155_000L step 250) assertNull(tick(t, hr = 125, res = 48))
        assertEquals(49, tick(155_000, hr = 125, res = 48))
    }

    @Test fun `a false return leaves the baseline alone`() {
        assertTrue(run(0, 19_750, hr = 125).isEmpty())
        assertEquals(41, tick(20_000, hr = 125))
        c.onCommandResult(false)
        assertNull(tick(25_000, hr = 125))
        assertEquals(ResistanceMode.AUTO, c.mode)
        for (t in 25_250L until 40_000L step 250) assertNull(tick(t, hr = 125))
        assertEquals(41, tick(40_000, hr = 125))
    }

    @Test fun `a resistance step emits its value once on entry and passes the clamps`() {
        val step = StepRef(0, false, 85)
        obs = 30
        assertEquals(85, tick(0, hr = null, cad = 0, step = step))
        deliver(85)
        for (t in 250L..300_000L step 250) assertNull(tick(t, hr = null, cad = 0, step = step))
        assertEquals(ResistanceMode.PAUSED, c.mode)
    }

    @Test fun `an entry command waits for the bike and retries after a false return`() {
        val step = StepRef(0, false, 30)
        for (t in 0L..4_750L step 250) {
            assertNull(tick(t, res = null, step = step))
            assertEquals(ResistanceMode.HOLD_NO_BIKE, c.mode)
        }
        assertEquals(30, tick(5_000, res = 20, step = step))
        c.onCommandResult(false)
        for (t in 5_250L..6_750L step 250) assertNull(tick(t, res = 20, step = step))
        assertEquals(30, tick(7_000, res = 20, step = step))
        c.onCommandResult(true)
        for (t in 7_250L..20_000L step 250) assertNull(tick(t, res = 30, step = step))
    }

    @Test fun `an entry command is dropped at the step change`() {
        val step = StepRef(0, false, 30)
        for (t in 0L..2_000L step 250) assertNull(tick(t, res = null, step = step))
        for (t in 2_250L..60_000L step 250) assertNull(tick(t, hr = 135, res = 20, step = hrStep))
    }

    @Test fun `a live band change applies at the next evaluation`() {
        commanded41()
        assertNull(tick(21_000, hr = 135))
        assertNull(tick(25_000, hr = 135, band = 140..150))
        assertEquals(ResistanceMode.AUTO, c.mode)
        for (t in 25_250L until 40_000L step 250) assertNull(tick(t, hr = 135, band = 140..150))
        assertEquals(42, tick(40_000, hr = 135, band = 140..150))
    }

    @Test fun `step change resets state`() {
        commanded41()
        tick(23_000, hr = 125, res = 46)
        assertTrue(c.mode is ResistanceMode.MANUAL)
        val next = StepRef(2, true, null)
        assertNull(tick(24_000, hr = 125, res = 46, step = next))
        assertEquals(ResistanceMode.AUTO, c.mode)
        // new baseline is the knob position, timer restarted at the step change
        for (t in 24_250L until 44_000L step 250) assertNull(tick(t, hr = 125, res = 46, step = next))
        assertEquals(47, tick(44_000, hr = 125, res = 46, step = next))
    }

    @Test fun `reset clears everything`() {
        commanded41()
        tick(23_000, hr = 125, res = 46)
        assertTrue(c.mode is ResistanceMode.MANUAL)
        c.reset()
        obs = 40
        assertTrue(run(30_000, 49_750, hr = 125).isEmpty())
        assertEquals(50_000L to 41, run(50_000, 50_000, hr = 125).single())
        assertEquals(ResistanceMode.AUTO, c.mode)
    }
}

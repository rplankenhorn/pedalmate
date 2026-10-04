package dev.pedalmate.audio

import android.media.ToneGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueTonesTest {
    private fun types(cue: Cue) = CueTones.sequenceFor(cue).map { it.type }

    @Test fun everyCueHasPositiveTones() {
        for (cue in Cue.entries) {
            val seq = CueTones.sequenceFor(cue)
            assertTrue("$cue empty", seq.isNotEmpty())
            assertTrue("$cue duration", seq.all { it.durationMs > 0 })
        }
    }

    @Test fun harderAndEasierAreMirrorImages() {
        assertEquals(types(Cue.STEP_HARDER), types(Cue.STEP_EASIER).reversed())
        assertNotEquals(types(Cue.STEP_HARDER), types(Cue.STEP_EASIER))
    }

    @Test fun sameDiffersFromHarder() = assertNotEquals(types(Cue.STEP_SAME), types(Cue.STEP_HARDER))

    @Test fun countdownIsSingleTone() = assertEquals(1, CueTones.sequenceFor(Cue.COUNTDOWN).size)

    @Test fun tableIsPinned() {
        assertEquals(listOf(Tone(ToneGenerator.TONE_PROP_ACK, 300)), CueTones.sequenceFor(Cue.START))
        assertEquals(listOf(Tone(ToneGenerator.TONE_DTMF_5, 120)), CueTones.sequenceFor(Cue.COUNTDOWN))
        assertEquals(
            listOf(Tone(ToneGenerator.TONE_DTMF_5, 150), Tone(ToneGenerator.TONE_DTMF_9, 250)),
            CueTones.sequenceFor(Cue.STEP_HARDER),
        )
        assertEquals(
            listOf(Tone(ToneGenerator.TONE_DTMF_9, 150), Tone(ToneGenerator.TONE_DTMF_5, 250)),
            CueTones.sequenceFor(Cue.STEP_EASIER),
        )
        assertEquals(listOf(Tone(ToneGenerator.TONE_DTMF_5, 300)), CueTones.sequenceFor(Cue.STEP_SAME))
        assertEquals(
            listOf(Tone(ToneGenerator.TONE_PROP_ACK, 200), Tone(ToneGenerator.TONE_PROP_BEEP2, 400)),
            CueTones.sequenceFor(Cue.FINISH),
        )
    }
}

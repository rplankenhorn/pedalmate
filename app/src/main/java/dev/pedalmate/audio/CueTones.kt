package dev.pedalmate.audio

import android.media.ToneGenerator

/** One tone: a [ToneGenerator] `TONE_*` constant and its length. */
data class Tone(val type: Int, val durationMs: Int)

/** Pure description of each cue as a tone sequence. */
object CueTones {
    fun sequenceFor(cue: Cue): List<Tone> = when (cue) {
        Cue.START -> listOf(Tone(ToneGenerator.TONE_PROP_ACK, 300))
        Cue.COUNTDOWN -> listOf(Tone(ToneGenerator.TONE_DTMF_5, 120))
        Cue.STEP_HARDER -> listOf(Tone(ToneGenerator.TONE_DTMF_5, 150), Tone(ToneGenerator.TONE_DTMF_9, 250))
        Cue.STEP_EASIER -> listOf(Tone(ToneGenerator.TONE_DTMF_9, 150), Tone(ToneGenerator.TONE_DTMF_5, 250))
        Cue.STEP_SAME -> listOf(Tone(ToneGenerator.TONE_DTMF_5, 300))
        Cue.FINISH -> listOf(Tone(ToneGenerator.TONE_PROP_ACK, 200), Tone(ToneGenerator.TONE_PROP_BEEP2, 400))
    }
}

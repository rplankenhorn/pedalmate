package dev.pedalmate.audio

/** Audible cues the app can play during a ride. */
enum class Cue { START, COUNTDOWN, STEP_HARDER, STEP_EASIER, STEP_SAME, FINISH }

/** Something that can play a [Cue]. */
fun interface CueSink { fun play(cue: Cue) }

package dev.pedalmate.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays cues on STREAM_MUSIC via [ToneGenerator]; a new cue pre-empts any sequence still playing.
 * After [release] the player is inert: [play] does nothing and never recreates a generator.
 */
class CuePlayer(private val scope: CoroutineScope) : CueSink {
    private var generator: ToneGenerator? = null
    private var job: Job? = null
    private var released = false

    override fun play(cue: Cue) {
        Log.i("PedalMate", "cue $cue")
        synchronized(this) {
            if (released) return
            job?.cancel()
            job = scope.launch(Dispatchers.Default) {
                val gen = obtain() ?: return@launch
                for (tone in CueTones.sequenceFor(cue)) {
                    // startTone and release share the monitor, so release() can never land mid-call
                    // and a released generator is never started.
                    val ok = synchronized(this@CuePlayer) {
                        if (generator !== gen) false
                        else try {
                            gen.startTone(tone.type, tone.durationMs)
                            true
                        } catch (e: RuntimeException) {
                            Log.w("PedalMate", "ToneGenerator failed", e)
                            false
                        }
                    }
                    if (!ok) return@launch
                    delay(tone.durationMs + 60L)
                }
            }
        }
    }

    @Synchronized
    private fun obtain(): ToneGenerator? {
        if (released) return null
        return generator ?: try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 100).also { generator = it }
        } catch (e: RuntimeException) {
            Log.w("PedalMate", "ToneGenerator unavailable", e)
            null
        }
    }

    /** Cancels playback and frees the tone generator; later [play] calls are ignored. */
    fun release() {
        synchronized(this) {
            released = true
            job?.cancel()
            job = null
            generator?.release()
            generator = null
        }
    }
}

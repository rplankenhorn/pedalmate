package dev.pedalmate.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Plays cues on STREAM_MUSIC via [ToneGenerator]; a new cue pre-empts any sequence still playing. */
class CuePlayer(private val scope: CoroutineScope) : CueSink {
    private var generator: ToneGenerator? = null
    private var job: Job? = null

    override fun play(cue: Cue) {
        Log.i("PedalMate", "cue $cue")
        synchronized(this) {
            job?.cancel()
            job = scope.launch(Dispatchers.Default) {
                val gen = obtain() ?: return@launch
                for (tone in CueTones.sequenceFor(cue)) {
                    gen.startTone(tone.type, tone.durationMs)
                    delay(tone.durationMs + 60L)
                }
            }
        }
    }

    @Synchronized
    private fun obtain(): ToneGenerator? = generator ?: try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100).also { generator = it }
    } catch (e: RuntimeException) {
        Log.w("PedalMate", "ToneGenerator unavailable", e)
        null
    }

    /** Cancels playback and frees the tone generator. */
    fun release() {
        synchronized(this) {
            job?.cancel()
            job = null
            generator?.release()
            generator = null
        }
    }
}

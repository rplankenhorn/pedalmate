package dev.pedalmate.ui.setup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.pedalmate.data.appContainer
import dev.pedalmate.ride.RideService
import dev.pedalmate.ui.debug.CueDebugRow
import dev.pedalmate.ui.debug.RideDebugPanel
import dev.pedalmate.ui.debug.SensorDebugScreen
import dev.pedalmate.ui.theme.PedalMateTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Launcher activity. Temporarily shows the sensor and ride debug screen; the real setup screen arrives in A14. */
class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = appContainer
        handleDebugIntent(intent)
        setContent {
            PedalMateTheme {
                val m by c.hub.bike.metrics.collectAsStateWithLifecycle()
                val st by c.hub.bike.connectionState.collectAsStateWithLifecycle()
                val snap by c.session.snapshot.collectAsStateWithLifecycle()
                Column(Modifier.fillMaxSize().background(Color.Black)) {
                    RideDebugPanel(
                        snap,
                        onStartWorkout = { RideService.start(applicationContext, "pz-43") },
                        onFreeRide = { RideService.start(applicationContext, null) },
                        onPause = c.session::pause,
                        onResume = c.session::resume,
                        onSkip = c.session::skip,
                        onStop = { startService(RideService.stopIntent(this@SetupActivity)) },
                    )
                    Box(Modifier.weight(1f)) {
                        SensorDebugScreen(m, st) {
                            CueDebugRow(
                                onPlay = c.cuePlayer::play,
                                onPlayDelayed = { cue -> lifecycleScope.launch { delay(5_000); c.cuePlayer.play(cue) } },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
    }

    /** Temporary debug hooks (A14 removes them): set an FTP, and start/stop rides, without UI. RideService is not exported, so adb goes through this Activity. */
    private fun handleDebugIntent(intent: Intent) {
        val c = appContainer
        intent.getIntExtra("debug_ftp", 0).takeIf { it > 0 }?.let { f ->
            lifecycleScope.launch {
                try {
                    c.settings.setFtp(f)
                } catch (e: IllegalArgumentException) {
                    Log.w("PedalMate", "debug_ftp rejected", e)
                }
            }
        }
        intent.getStringExtra("debug_start")?.let { RideService.start(applicationContext, it.takeIf { id -> id != "free" }) }
        if (intent.getBooleanExtra("debug_stop", false)) startService(RideService.stopIntent(this))
    }

    override fun onStart() {
        super.onStart()
        appContainer.hub.acquire()
    }

    override fun onStop() {
        appContainer.hub.release()
        super.onStop()
    }
}

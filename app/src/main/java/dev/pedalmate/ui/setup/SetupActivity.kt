package dev.pedalmate.ui.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.pedalmate.sensor.BoundBikeDataSource
import dev.pedalmate.sensor.SensorFactory
import dev.pedalmate.ui.debug.SensorDebugScreen
import dev.pedalmate.ui.theme.PedalMateTheme

/** Launcher activity. Temporarily shows the sensor debug screen; the real setup screen arrives in A14. */
class SetupActivity : ComponentActivity() {
    private lateinit var bike: BoundBikeDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bike = SensorFactory.createBikeSource(applicationContext, lifecycleScope)
        setContent {
            PedalMateTheme {
                val metrics by bike.metrics.collectAsStateWithLifecycle()
                val state by bike.connectionState.collectAsStateWithLifecycle()
                SensorDebugScreen(metrics, state)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bike.start()
    }

    override fun onStop() {
        bike.stop()
        super.onStop()
    }
}

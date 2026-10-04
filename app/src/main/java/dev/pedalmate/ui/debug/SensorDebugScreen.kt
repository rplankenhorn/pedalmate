package dev.pedalmate.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedalmate.audio.Cue
import dev.pedalmate.sensor.BikeMetrics
import dev.pedalmate.sensor.ConnectionState

/** Display strings for the debug screen; dashes and a banner whenever the sensor is not live. */
data class DebugText(val power: String, val cadence: String, val resistance: String, val banner: String?)

/** Never shows the last known values unless [state] is Connected. */
fun debugText(m: BikeMetrics, state: ConnectionState): DebugText = when (state) {
    ConnectionState.Connected ->
        DebugText("${m.powerWatts} W", "${m.cadenceRpm}", "${m.resistancePercent}", null)
    ConnectionState.Unavailable -> DebugText("--", "--", "--", "NO SENSOR")
    ConnectionState.Disconnected -> DebugText("--", "--", "--", "SENSOR LOST")
}

/** Temporary launcher content for the on-bike sensor proof: POWER / CADENCE / RESISTANCE. */
@Composable
fun SensorDebugScreen(
    metrics: BikeMetrics,
    state: ConnectionState,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val text = debugText(metrics, state)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (text.banner != null) {
            Text(text.banner, color = Color.Red, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        DebugBlock("POWER", text.power)
        DebugBlock("CADENCE", text.cadence)
        DebugBlock("RESISTANCE", text.resistance)
        extra()
    }
}

@Composable
private fun DebugBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.Gray, fontSize = 28.sp, textAlign = TextAlign.Center)
        Text(value, color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Debug-only buttons to audition each cue; the delayed one lets you switch apps before it sounds. */
@Composable
fun CueDebugRow(onPlay: (Cue) -> Unit, onPlayDelayed: (Cue) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CueButton("START") { onPlay(Cue.START) }
        CueButton("COUNTDOWN") { onPlay(Cue.COUNTDOWN) }
        CueButton("HARDER") { onPlay(Cue.STEP_HARDER) }
        CueButton("EASIER") { onPlay(Cue.STEP_EASIER) }
        CueButton("SAME") { onPlay(Cue.STEP_SAME) }
        CueButton("FINISH") { onPlay(Cue.FINISH) }
        CueButton("HARDER in 5 s") { onPlayDelayed(Cue.STEP_HARDER) }
    }
}

@Composable
private fun CueButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, fontSize = 16.sp)
    }
}

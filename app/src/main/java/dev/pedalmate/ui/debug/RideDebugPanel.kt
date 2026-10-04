package dev.pedalmate.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedalmate.ride.RideSnapshot

/** Temporary ride controls and status text (A14 removes it). */
@Composable
fun RideDebugPanel(
    snapshot: RideSnapshot,
    onStartWorkout: () -> Unit,
    onFreeRide: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    val w = snapshot.workout
    val range = snapshot.targetRange
    val lines = listOf(
        "status=${snapshot.status} workout=${snapshot.workoutName}",
        "step=${w?.currentStep?.label} remaining=${w?.stepRemainingMs?.div(1000)}s",
        "target=${range?.let { "${it.lowWatts}-${it.highWatts}" }} status=${snapshot.targetStatus} bike=${snapshot.bikeState}",
    )
    Column {
        Text(lines.joinToString("\n"), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DebugButton("START PZ-43", onStartWorkout)
            DebugButton("FREE RIDE", onFreeRide)
            DebugButton("PAUSE", onPause)
            DebugButton("RESUME", onResume)
            DebugButton("SKIP", onSkip)
            DebugButton("STOP", onStop)
        }
    }
}

@Composable
private fun DebugButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, fontSize = 16.sp)
    }
}

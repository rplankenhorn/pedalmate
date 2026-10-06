package dev.pedalmate.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedalmate.heartrate.BleDevice
import dev.pedalmate.heartrate.PairingState
import dev.pedalmate.overlay.ZoneColors
import dev.pedalmate.permissions.PermissionItem
import dev.pedalmate.ride.RideStatus
import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.workout.PowerZone

/** Heart-rate strap status shown on the setup screen. */
data class HrCardState(val deviceLabel: String?, val connection: ConnectionState, val bpm: Int?)

/** Callbacks from the setup screen. */
data class SetupActions(
    val onFtpChange: (String) -> Unit,
    val onSelect: (String) -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onSkip: () -> Unit,
    val onLaunchLichess: () -> Unit,
    val onFixPermission: (PermissionItem) -> Unit,
    val onPackageChange: (String) -> Unit,
    val onDismissMessage: () -> Unit,
    val onShowDiagnostics: () -> Unit,
    val onPairHr: () -> Unit,
    val onScanHr: () -> Unit,
    val onPickStrap: (BleDevice) -> Unit,
    val onForgetHr: () -> Unit,
    val onClosePairing: () -> Unit,
)

private val amber = Color(0xFFFFB300)

/** The launcher screen: FTP, workout picker, ride controls, permissions and sensors. Landscape, two columns, inline only. */
@Composable
fun SetupScreen(state: SetupUiState, hr: HrCardState, pairing: PairingState, permissions: List<PermissionItem>, actions: SetupActions) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FtpCard(state, actions)
                WorkoutPicker(state, actions, Modifier.weight(1f))
            }
            RightColumn(state, hr, pairing, permissions, actions, Modifier.weight(1f).fillMaxHeight())
        }
        state.message?.let { m ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m, color = amber, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = actions.onDismissMessage) { Text("DISMISS") }
            }
        }
    }
}

@Composable
private fun FtpCard(state: SetupUiState, actions: SetupActions) {
    OutlinedTextField(
        value = state.ftpText,
        onValueChange = actions.onFtpChange,
        label = { Text("FTP (watts)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = state.ftpError != null,
        supportingText = { Text(state.ftpError ?: "Functional threshold power, 50 to 600 W") },
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.zoneRows.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.zoneRows.chunked(3).forEach { chunk ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEach { row ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).background(ZoneColors.of(PowerZone.values().getOrNull(row.zone - 1))), contentAlignment = Alignment.Center) {
                                Text("Z${row.zone}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(row.rangeText, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    repeat(3 - chunk.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun WorkoutPicker(state: SetupUiState, actions: SetupActions, modifier: Modifier) {
    Column(modifier) {
        Text("WORKOUT", color = Color.Gray, fontSize = 14.sp)
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(state.workouts, key = { it.id }) { w ->
                val selected = w.id == state.selectedId
                Row(
                    Modifier.fillMaxWidth().selectable(selected = selected, onClick = { actions.onSelect(w.id) }).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(w.title, color = Color.White, fontSize = 20.sp)
                        Text(w.subtitle, color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        }
        state.loadErrors.forEach { Text(it, color = Color.Red, fontSize = 14.sp) }
    }
}

@Composable
private fun RightColumn(state: SetupUiState, hr: HrCardState, pairing: PairingState, permissions: List<PermissionItem>, actions: SetupActions, modifier: Modifier) {
    var advanced by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val inRide = state.rideStatus == RideStatus.RUNNING || state.rideStatus == RideStatus.PAUSED
        state.startWarning?.let { Text(it, color = amber, fontSize = 16.sp) }
        state.startBlockedReason?.let { Text(it, color = Color.Gray, fontSize = 16.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!inRide) {
                Button(onClick = actions.onStart, enabled = state.canStart) { Text("START RIDE") }
            }
            if (state.rideStatus != RideStatus.IDLE) {
                if (state.showWorkoutControls) {
                    if (state.rideStatus == RideStatus.PAUSED) {
                        Button(onClick = actions.onResume) { Text("RESUME") }
                    } else if (state.rideStatus == RideStatus.RUNNING) {
                        Button(onClick = actions.onPause) { Text("PAUSE") }
                    }
                    Button(onClick = actions.onSkip) { Text("SKIP") }
                }
                Button(onClick = actions.onStop) { Text("STOP") }
            }
        }
        PermissionsCard(permissions, actions.onFixPermission)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hrStatusText(hr), color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = actions.onPairHr) { Text("PAIR HR STRAP") }
        }
        if (pairing != PairingState.Idle) {
            HrPairingPanel(pairing, state.hrDeviceLabel, actions.onScanHr, actions.onPickStrap, actions.onForgetHr, actions.onClosePairing)
        }
        Button(onClick = actions.onLaunchLichess) { Text("LAUNCH LICHESS") }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "ADVANCED (hide)" else "ADVANCED") }
        if (advanced) {
            OutlinedTextField(
                value = state.lichessPackage,
                onValueChange = actions.onPackageChange,
                label = { Text("Lichess package") },
                singleLine = true,
                isError = state.lichessPackageError != null,
                supportingText = state.lichessPackageError?.let { e -> { Text(e) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = actions.onShowDiagnostics) { Text("SENSOR DIAGNOSTICS") }
        }
    }
}

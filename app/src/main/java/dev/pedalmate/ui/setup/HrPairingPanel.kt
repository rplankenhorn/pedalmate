package dev.pedalmate.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedalmate.heartrate.BleDevice
import dev.pedalmate.heartrate.PairingState

/** Inline heart-rate strap pairing: scan results, selection, and forget. No dialogs (the Bike+ has flaky popups). */
@Composable
fun HrPairingPanel(
    pairing: PairingState,
    savedLabel: String?,
    onScan: () -> Unit,
    onPick: (BleDevice) -> Unit,
    onForget: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onScan, enabled = pairing !is PairingState.Scanning) { Text("SCAN") }
            if (savedLabel != null) OutlinedButton(onClick = onForget) { Text("FORGET") }
            TextButton(onClick = onClose) { Text("CLOSE") }
        }
        when (pairing) {
            is PairingState.Scanning -> {
                Text("Scanning...", color = Color.Gray, fontSize = 16.sp)
                DeviceList(pairing.devices, onPick)
            }
            is PairingState.Done -> {
                if (pairing.devices.isEmpty()) Text("No heart-rate straps found", color = Color.Gray, fontSize = 16.sp)
                DeviceList(pairing.devices, onPick)
            }
            is PairingState.Failed -> Text(pairing.reason, color = Color.Red, fontSize = 16.sp)
            is PairingState.Unavailable -> Text(pairing.reason, color = Color.Red, fontSize = 16.sp)
            is PairingState.Paired -> Text("Paired with ${pairing.device.name ?: pairing.device.address}", color = Color.White, fontSize = 16.sp)
            PairingState.Idle -> Unit
        }
    }
}

@Composable
private fun DeviceList(devices: List<BleDevice>, onPick: (BleDevice) -> Unit) {
    devices.forEach { d ->
        Row(Modifier.fillMaxWidth().clickable { onPick(d) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(d.name ?: "Unknown strap", color = Color.White, fontSize = 18.sp)
            Text(d.address, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

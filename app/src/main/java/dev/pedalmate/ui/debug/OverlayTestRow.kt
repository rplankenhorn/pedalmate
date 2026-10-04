package dev.pedalmate.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Temporary buttons to show and hide the test overlay (A12 removes it). */
@Composable
fun OverlayTestRow(onShow: () -> Unit, onHide: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onShow) { Text("SHOW TEST OVERLAY") }
        Button(onClick = onHide) { Text("HIDE TEST OVERLAY") }
    }
}

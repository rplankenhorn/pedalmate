package dev.pedalmate.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedalmate.permissions.Fix
import dev.pedalmate.permissions.PermissionItem

private val amber = Color(0xFFFFB300)
private val green = Color(0xFF66BB6A)

/** One row per permission: status, plus the fix that applies (system screen, runtime request or adb command). */
@Composable
fun PermissionsCard(items: List<PermissionItem>, onFix: (PermissionItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(if (item.granted) "OK" else "NEEDED", color = if (item.granted) green else amber, fontSize = 16.sp)
                when (item.fix) {
                    is Fix.OpenScreen -> OutlinedButton(onClick = { onFix(item) }) { Text("OPEN SETTINGS") }
                    is Fix.RequestRuntime -> OutlinedButton(onClick = { onFix(item) }) { Text("ALLOW") }
                    else -> Unit
                }
            }
            val fix = item.fix
            if (fix is Fix.ShowAdb) {
                SelectionContainer { Text(fix.command, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace) }
                Text("Run this from your PC with adb (OpenPelo), then come back.", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

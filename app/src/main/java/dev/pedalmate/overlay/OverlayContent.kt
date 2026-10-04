package dev.pedalmate.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.TargetStatus

private val PanelBackground = Color(0xCC000000)
private val WarnRed = Color(0xFFEF5350)

private fun zoneColor(z: PowerZone?): Color = when (z?.number) {
    1 -> Color(0xFF9E9E9E); 2 -> Color(0xFF2196F3); 3 -> Color(0xFF4CAF50); 4 -> Color(0xFFFFC107)
    5 -> Color(0xFFFF9800); 6 -> Color(0xFFF44336); 7 -> Color(0xFF9C27B0); else -> Color(0xFF616161)
}

private fun statusColor(s: TargetStatus?): Color = when (s) {
    TargetStatus.BELOW -> Color(0xFF42A5F5)
    TargetStatus.IN -> Color(0xFF66BB6A)
    TargetStatus.ABOVE -> WarnRed
    null -> Color.White
}

/** Ride overlay: an expanded column, or a one-line pill when [minimized]. Tapping toggles via [onToggle]. */
@Composable
fun OverlayContent(model: OverlayUiModel, minimized: Boolean, onToggle: () -> Unit) {
    if (minimized) Pill(model, onToggle) else Panel(model, onToggle)
}

@Composable
private fun Panel(model: OverlayUiModel, onToggle: () -> Unit) {
    Column(
        Modifier.width(240.dp)
            .background(PanelBackground, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (model.banner != null) Text(model.banner, color = WarnRed, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.background(zoneColor(model.zone), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(model.zoneChip, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Text(model.wattsText, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text("W", color = Color.White, fontSize = 14.sp)
        }
        if (model.targetText != null) {
            Text("Target ${model.targetText}", color = Color.White, fontSize = 16.sp)
            if (model.statusText != null) {
                Text(model.statusText, color = statusColor(model.targetStatus), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(model.intervalName, color = Color.White, fontSize = 20.sp)
        if (model.intervalTime != null) Text(model.intervalTime, color = Color.White, fontSize = 36.sp)
        if (model.nextText != null) Text(model.nextText, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("HR ${model.hrText}", color = Color.White, fontSize = 14.sp)
            Text("RPM ${model.cadenceText}", color = Color.White, fontSize = 14.sp)
            Text("RES ${model.resistanceText}", color = Color.White, fontSize = 14.sp)
        }
        if (model.needsFtp) Text("Set FTP in PedalMate", color = Color(0xFFFFC107), fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(model.progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
        }
    }
}

@Composable
private fun Pill(model: OverlayUiModel, onToggle: () -> Unit) {
    Row(
        Modifier.background(PanelBackground, RoundedCornerShape(20.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(14.dp).background(zoneColor(model.zone), CircleShape))
        Text(
            model.pillText,
            color = if (model.banner != null) WarnRed else Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

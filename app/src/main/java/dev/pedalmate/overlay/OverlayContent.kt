package dev.pedalmate.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.pedalmate.workout.PowerZone

private val PanelBackground = Color(0xD1000000)
private val IntervalYellow = ZoneColors.Paused
private val LabelGrey = Color(0xFF9B9B9B)
private val AvgBestGrey = Color(0xFF8F8F8F)
private val Divider = Color(0x33FFFFFF)

/** Ride overlay: an expanded Peloton-style panel, or a one-line pill when [minimized]. Tapping toggles via [onToggle]. */
@Composable
fun OverlayContent(model: OverlayUiModel, minimized: Boolean, onToggle: () -> Unit) {
    if (minimized) Pill(model, onToggle) else Panel(model, onToggle)
}

@Composable
private fun Panel(model: OverlayUiModel, onToggle: () -> Unit) {
    Column(
        Modifier.width(640.dp)
            .background(PanelBackground, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IntervalStrip(model)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
        if (model.banner != null) {
            Text(model.banner, color = ZoneColors.Above, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        OutputRow(model)
        ZoneRow(model)
        BottomRow(model)
        if (model.needsFtp) Text("Set FTP in PedalMate", color = IntervalYellow, fontSize = 12.sp)
    }
}

@Composable
private fun IntervalStrip(model: OverlayUiModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(model.intervalName, color = IntervalYellow, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (model.intervalTime != null) {
            Text(model.intervalTime, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
        }
        if (model.nextText != null) Text(model.nextText, color = LabelGrey, fontSize = 14.sp)
    }
}

@Composable
private fun OutputRow(model: OverlayUiModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("OUTPUT (WATTS)", color = LabelGrey, fontSize = 12.sp, letterSpacing = 0.12.em)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AVG", color = LabelGrey, fontSize = 11.sp, letterSpacing = 0.12.em)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ZoneColors.of(PowerZone.Z3), fontSize = 10.sp)) { append("\u25B2 ") }
                        append(model.avgWattsText)
                    },
                    color = AvgBestGrey, fontSize = 22.sp, fontWeight = FontWeight.Light,
                )
            }
            Text(model.wattsText, color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BEST", color = LabelGrey, fontSize = 11.sp, letterSpacing = 0.12.em)
                Text(model.bestWattsText, color = AvgBestGrey, fontSize = 22.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

@Composable
private fun ZoneRow(model: OverlayUiModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text("ZONE", color = LabelGrey, fontSize = 11.sp, letterSpacing = 0.12.em)
            Text(model.zoneNumberText, color = ZoneColors.of(model.zone), fontSize = 40.sp, fontWeight = FontWeight.Light)
            if (model.statusText != null) {
                val color = if (model.paused) ZoneColors.Paused else ZoneColors.status(model.targetStatus)
                Text(model.statusText, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        PelotonZoneBar(
            current = model.zone, target = model.targetZone, labels = model.zoneBoundaryLabels,
            modifier = Modifier.weight(1f),
        )
        Column {
            Text("FTP %", color = LabelGrey, fontSize = 11.sp, letterSpacing = 0.12.em)
            Text(model.ftpPercentText, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun BottomRow(model: OverlayUiModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Metric("CADENCE", model.cadenceText, " rpm")
        Metric("RESISTANCE", model.resistanceText, "")
        Metric("HR", model.hrText, " bpm")
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = LabelGrey, letterSpacing = 0.1.em)) { append(label) }
            append("  ")
            withStyle(SpanStyle(color = Color.White)) { append(value) }
            withStyle(SpanStyle(color = LabelGrey)) { append(unit) }
        },
        fontSize = 13.sp,
    )
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
        Box(Modifier.size(14.dp).background(ZoneColors.of(model.zone), CircleShape))
        Text(
            model.pillText,
            color = if (model.banner != null) ZoneColors.Above else Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

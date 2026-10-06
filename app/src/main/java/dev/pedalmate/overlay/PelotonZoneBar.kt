package dev.pedalmate.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedalmate.workout.PowerZone

private const val SEGMENTS = 7
private val EndRadius = 6.dp
private val Gap = 3.dp
private val LabelGrey = Color(0xFFCFCFCF)

/** Rounded on the outer edge of the first and last segment, square elsewhere. */
internal fun segmentShape(index: Int): RoundedCornerShape = RoundedCornerShape(
    topStart = if (index == 0) EndRadius else 0.dp,
    bottomStart = if (index == 0) EndRadius else 0.dp,
    topEnd = if (index == SEGMENTS - 1) EndRadius else 0.dp,
    bottomEnd = if (index == SEGMENTS - 1) EndRadius else 0.dp,
)

/**
 * Seven zone segments with the [target] zone outlined (the [current] zone is shown by the zone number,
 * as on Peloton) and the lower watt edge of each zone beneath its segment's left edge.
 */
@Composable
fun PelotonZoneBar(current: PowerZone?, target: PowerZone?, labels: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until SEGMENTS) {
                val zone = PowerZone.values()[i]
                val shape: Shape = segmentShape(i)
                val isTarget = zone == target
                Box(
                    Modifier.weight(1f)
                        .height(if (isTarget) 22.dp else 18.dp)
                        .background(ZoneColors.of(zone), shape)
                        .then(if (isTarget) Modifier.border(2.dp, Color.White, shape) else Modifier),
                )
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                for (i in 0 until SEGMENTS) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Text(labels.getOrElse(i) { "" }, color = LabelGrey, fontSize = 12.sp, softWrap = false)
                    }
                }
            }
        }
    }
}

package dev.pedalmate.overlay

import androidx.compose.ui.graphics.Color
import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.TargetStatus

/** Peloton Power Zone palette (Feb 2024) plus the target-status colors that go with it. */
object ZoneColors {
    private val zones = listOf(
        Color(0xFF8183F9), Color(0xFF31BCFD), Color(0xFF0BDAA6), Color(0xFFA8D90C),
        Color(0xFFFDC619), Color(0xFFFE8935), Color(0xFFEB4756),
    )
    private val unknown = Color(0xFF616161)

    val Below = Color(0xFF31BCFD)
    val In = Color(0xFF0BDAA6)
    val Above = Color(0xFFEB4756)
    val Paused = Color(0xFFF5C232)

    fun of(zone: PowerZone?): Color = if (zone == null) unknown else zones[zone.number - 1]

    fun status(status: TargetStatus?): Color = when (status) {
        TargetStatus.BELOW -> Below
        TargetStatus.IN -> In
        TargetStatus.ABOVE -> Above
        null -> Color.White
    }
}

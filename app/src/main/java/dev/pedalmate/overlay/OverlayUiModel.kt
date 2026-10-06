package dev.pedalmate.overlay

import dev.pedalmate.ride.RideSnapshot
import dev.pedalmate.ride.RideStatus
import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.TargetStatus
import dev.pedalmate.workout.ZoneTable
import kotlin.math.roundToInt

private const val NO_SENSOR = "NO SENSOR"

/** Display-ready strings for the overlay, derived purely from a [RideSnapshot]. */
data class OverlayUiModel(
    val zoneChip: String,
    val zone: PowerZone?,
    val wattsText: String,
    val banner: String?,
    val targetText: String?,
    val targetStatus: TargetStatus?,
    val statusText: String?,
    val paused: Boolean,
    val intervalName: String,
    val intervalTime: String?,
    val nextText: String?,
    val hrText: String,
    val cadenceText: String,
    val resistanceText: String,
    val avgWattsText: String,
    val bestWattsText: String,
    val ftpPercentText: String,
    val zoneBoundaryLabels: List<String>,
    val targetZone: PowerZone?,
    val zoneNumberText: String,
    val needsFtp: Boolean,
    val pillText: String,
) {
    companion object {
        private fun ftpPercentText(watts: Int?, ftp: Int?): String =
            if (watts == null || ftp == null || ftp <= 0) "--" else (watts * 100.0 / ftp).roundToInt().toString()

        /** Lower watt edge of each of the 7 zones; Z1 starts at 0. Empty without a valid FTP. */
        private fun zoneBoundaryLabels(ftp: Int?): List<String> {
            val table = ZoneTable.forFtp(ftp) ?: return emptyList()
            return PowerZone.values().map { if (it.number == 1) "0" else table.rangeOf(it).lowWatts.toString() }
        }

        fun from(s: RideSnapshot): OverlayUiModel {
            val bikeLive = s.bikeState == ConnectionState.Connected
            val zoneChip = s.currentZone?.let { "Z${it.number}" } ?: "--"
            val wattsText = s.smoothedPowerWatts?.toString() ?: "--"
            val w = s.workout
            val step = w?.currentStep
            val targetText = when {
                s.targetRange != null -> formatRange(s.targetRange)
                s.targetZone != null -> "Z${s.targetZone.number}"
                else -> null
            }
            val intervalName = when {
                s.status == RideStatus.IDLE -> "Ready"
                s.status == RideStatus.FINISHED -> "Done"
                w == null -> "Free ride"
                else -> step?.label ?: "Ready"
            }
            val running = s.status == RideStatus.RUNNING || s.status == RideStatus.PAUSED
            val intervalTime = when {
                w != null && step != null -> formatClock(w.stepRemainingMs)
                w == null && running -> formatClock(s.elapsedMs)
                else -> null
            }
            val nextText = if (w != null && step != null) {
                w.nextStep?.let { "Next: ${it.label} ${formatClock(it.seconds * 1000L)}" } ?: "Last interval"
            } else {
                null
            }
            val pillText = when {
                !bikeLive -> NO_SENSOR
                wattsText == "--" -> "$zoneChip \u00B7 --"
                else -> "$zoneChip \u00B7 $wattsText W"
            }
            return OverlayUiModel(
                zoneChip = zoneChip,
                zone = s.currentZone,
                wattsText = wattsText,
                banner = if (bikeLive) null else NO_SENSOR,
                targetText = targetText,
                targetStatus = s.targetStatus,
                statusText = if (s.status == RideStatus.PAUSED) {
                    "PAUSED"
                } else {
                    when (s.targetStatus) {
                        TargetStatus.BELOW -> "BELOW"
                        TargetStatus.IN -> "IN ZONE"
                        TargetStatus.ABOVE -> "ABOVE"
                        null -> null
                    }
                },
                paused = s.status == RideStatus.PAUSED,
                intervalName = intervalName,
                intervalTime = intervalTime,
                nextText = nextText,
                hrText = s.heartRateBpm?.toString() ?: "--",
                cadenceText = s.cadenceRpm?.toString() ?: "--",
                resistanceText = s.resistancePercent?.let { "$it%" } ?: "--",
                avgWattsText = s.avgPowerWatts?.toString() ?: "--",
                bestWattsText = s.maxPowerWatts?.toString() ?: "--",
                ftpPercentText = ftpPercentText(s.smoothedPowerWatts, s.ftp),
                zoneBoundaryLabels = zoneBoundaryLabels(s.ftp),
                targetZone = s.targetZone ?: s.targetRange?.zone,
                zoneNumberText = s.currentZone?.number?.toString() ?: "--",
                needsFtp = s.ftp == null,
                pillText = pillText,
            )
        }
    }
}

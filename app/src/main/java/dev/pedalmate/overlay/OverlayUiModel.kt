package dev.pedalmate.overlay

import dev.pedalmate.ride.RideSnapshot
import dev.pedalmate.ride.RideStatus
import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.TargetStatus

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
    val intervalName: String,
    val intervalTime: String?,
    val nextText: String?,
    val hrText: String,
    val cadenceText: String,
    val resistanceText: String,
    val progress: Float,
    val needsFtp: Boolean,
    val pillText: String,
) {
    companion object {
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
                wattsText == "--" -> "$zoneChip  --"
                else -> "$zoneChip  $wattsText W"
            }
            return OverlayUiModel(
                zoneChip = zoneChip,
                zone = s.currentZone,
                wattsText = wattsText,
                banner = if (bikeLive) null else NO_SENSOR,
                targetText = targetText,
                targetStatus = s.targetStatus,
                statusText = when (s.targetStatus) {
                    TargetStatus.BELOW -> "BELOW"
                    TargetStatus.IN -> "IN ZONE"
                    TargetStatus.ABOVE -> "ABOVE"
                    null -> null
                },
                intervalName = intervalName,
                intervalTime = intervalTime,
                nextText = nextText,
                hrText = s.heartRateBpm?.toString() ?: "--",
                cadenceText = s.cadenceRpm?.toString() ?: "--",
                resistanceText = s.resistancePercent?.let { "$it%" } ?: "--",
                progress = w?.progress ?: 0f,
                needsFtp = s.ftp == null,
                pillText = pillText,
            )
        }
    }
}

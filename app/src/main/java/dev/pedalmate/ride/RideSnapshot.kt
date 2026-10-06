package dev.pedalmate.ride

import dev.pedalmate.sensor.ConnectionState
import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.TargetStatus
import dev.pedalmate.workout.WorkoutState
import dev.pedalmate.workout.ZoneRange

/** Lifecycle of a ride as seen by the UI. */
enum class RideStatus { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * Everything the overlay needs, published by [RideSession]. Live sensor fields are null whenever
 * the owning sensor is not connected, so stale numbers never look live.
 */
data class RideSnapshot(
    val status: RideStatus,
    val workoutName: String?,
    val bikeState: ConnectionState,
    val hrState: ConnectionState,
    val powerWatts: Int?,
    val smoothedPowerWatts: Int?,
    val avgPowerWatts: Int?,
    val maxPowerWatts: Int?,
    val cadenceRpm: Int?,
    val resistancePercent: Int?,
    val heartRateBpm: Int?,
    val ftp: Int?,
    val currentZone: PowerZone?,
    val targetZone: PowerZone?,
    val targetRange: ZoneRange?,
    val targetStatus: TargetStatus?,
    val workout: WorkoutState?,
    val elapsedMs: Long,
) {
    companion object {
        fun idle(ftp: Int? = null) = RideSnapshot(
            RideStatus.IDLE, null, ConnectionState.Unavailable, ConnectionState.Unavailable,
            null, null, null, null, null, null, null, ftp, null, null, null, null, null, 0L,
        )
    }
}

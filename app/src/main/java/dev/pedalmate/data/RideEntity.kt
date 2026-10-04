package dev.pedalmate.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One recorded ride. [endedAt] is null while the ride is unfinished. */
@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val workoutId: String? = null,
    val ftp: Int? = null,
    val sampleCount: Int = 0,
    val avgPowerWatts: Int? = null,
    val maxPowerWatts: Int? = null,
    val avgCadenceRpm: Int? = null,
    val avgResistancePercent: Int? = null,
    val avgHeartRateBpm: Int? = null,
)

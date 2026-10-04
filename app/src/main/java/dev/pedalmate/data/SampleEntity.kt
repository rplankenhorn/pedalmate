package dev.pedalmate.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** One-per-second ride sample. A null value means the sensor was not live at that second. */
@Entity(
    tableName = "samples",
    primaryKeys = ["rideId", "tSec"],
    indices = [Index("rideId")],
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SampleEntity(
    val rideId: Long,
    val tSec: Int,
    val powerWatts: Int?,
    val cadenceRpm: Int?,
    val resistancePercent: Int?,
    val heartRateBpm: Int?,
    val zone: Int?,
)

package dev.pedalmate.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert suspend fun insert(ride: RideEntity): Long

    @Query(
        "UPDATE rides SET endedAt = :endedAt, sampleCount = :sampleCount, avgPowerWatts = :avgPower, " +
            "maxPowerWatts = :maxPower, avgCadenceRpm = :avgCadence, avgResistancePercent = :avgResistance, " +
            "avgHeartRateBpm = :avgHr WHERE id = :id"
    )
    suspend fun finish(
        id: Long, endedAt: Long, sampleCount: Int, avgPower: Int?, maxPower: Int?,
        avgCadence: Int?, avgResistance: Int?, avgHr: Int?,
    )

    @Query("SELECT * FROM rides WHERE endedAt IS NULL ORDER BY id") suspend fun unfinished(): List<RideEntity>
    @Query("SELECT * FROM rides WHERE id = :id") suspend fun byId(id: Long): RideEntity?
    @Query("SELECT * FROM rides ORDER BY startedAt DESC") fun observeAll(): Flow<List<RideEntity>>
}

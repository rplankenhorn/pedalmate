package dev.pedalmate.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(samples: List<SampleEntity>)
    @Query("SELECT * FROM samples WHERE rideId = :rideId ORDER BY tSec") suspend fun forRide(rideId: Long): List<SampleEntity>
    @Query("SELECT COUNT(*) FROM samples WHERE rideId = :rideId") suspend fun count(rideId: Long): Int
}

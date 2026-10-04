package dev.pedalmate.testutil

import dev.pedalmate.data.RideDao
import dev.pedalmate.data.RideEntity
import dev.pedalmate.data.SampleDao
import dev.pedalmate.data.SampleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeRideDao : RideDao {
    val rows = mutableListOf<RideEntity>()
    var finishCalls = 0
    var failFinishForId: Long? = null
    private var nextId = 1L

    override suspend fun insert(ride: RideEntity): Long {
        val id = nextId++
        rows += ride.copy(id = id)
        return id
    }

    override suspend fun finish(
        id: Long, endedAt: Long, sampleCount: Int, avgPower: Int?, maxPower: Int?,
        avgCadence: Int?, avgResistance: Int?, avgHr: Int?,
    ) {
        finishCalls++
        if (id == failFinishForId) throw IllegalStateException("boom $id")
        val i = rows.indexOfFirst { it.id == id }
        rows[i] = rows[i].copy(
            endedAt = endedAt, sampleCount = sampleCount, avgPowerWatts = avgPower, maxPowerWatts = maxPower,
            avgCadenceRpm = avgCadence, avgResistancePercent = avgResistance, avgHeartRateBpm = avgHr,
        )
    }

    override suspend fun unfinished(): List<RideEntity> = rows.filter { it.endedAt == null }
    override suspend fun byId(id: Long): RideEntity? = rows.firstOrNull { it.id == id }
    override fun observeAll(): Flow<List<RideEntity>> = flowOf(rows.toList())
}

class FakeSampleDao : SampleDao {
    val rows = mutableListOf<SampleEntity>()
    val batchSizes = mutableListOf<Int>()
    var failNextInsert = false

    override suspend fun insertAll(samples: List<SampleEntity>) {
        if (failNextInsert) { failNextInsert = false; throw IllegalStateException("disk full") }
        batchSizes += samples.size
        for (s in samples) {
            rows.removeAll { it.rideId == s.rideId && it.tSec == s.tSec }
            rows += s
        }
    }

    override suspend fun forRide(rideId: Long): List<SampleEntity> =
        rows.filter { it.rideId == rideId }.sortedBy { it.tSec }

    override suspend fun count(rideId: Long): Int = rows.count { it.rideId == rideId }
}

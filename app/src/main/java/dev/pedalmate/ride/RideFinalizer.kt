package dev.pedalmate.ride

import android.util.Log
import dev.pedalmate.data.RideDao
import dev.pedalmate.data.SampleDao
import kotlinx.coroutines.CancellationException

/** Closes rides left unfinished by a crash or process death, ending them at their last stored sample. */
class RideFinalizer(private val rides: RideDao, private val samples: SampleDao) {
    /** Returns how many rides were closed. One failing ride does not stop the others. */
    suspend fun finalizeUnfinished(): Int {
        var closed = 0
        for (ride in rides.unfinished()) {
            try {
                val stored = samples.forRide(ride.id)
                val aggregator = LiveAggregator()
                for (s in stored) aggregator.add(RideFrame(s.powerWatts, s.cadenceRpm, s.resistancePercent, s.heartRateBpm, s.zone))
                val agg = aggregator.snapshot()
                val endedAt = ride.startedAt + (stored.lastOrNull()?.tSec ?: 0) * 1000L
                rides.finish(ride.id, endedAt, agg.sampleCount, agg.avgPowerWatts, agg.maxPowerWatts, agg.avgCadenceRpm, agg.avgResistancePercent, agg.avgHeartRateBpm)
                closed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PedalMate", "could not finalise ride ${ride.id}", e)
            }
        }
        return closed
    }
}

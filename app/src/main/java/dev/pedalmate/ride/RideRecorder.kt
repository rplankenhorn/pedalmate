package dev.pedalmate.ride

import android.util.Log
import dev.pedalmate.data.RideDao
import dev.pedalmate.data.RideEntity
import dev.pedalmate.data.SampleDao
import dev.pedalmate.data.SampleEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Sink for ride frames; implemented by [RideRecorder]. */
interface RideLog {
    suspend fun begin(workoutId: String?, ftp: Int?): Long
    fun offer(frame: RideFrame)
    suspend fun finish(): LiveAggregates?
}

/** Records one sample per ride-second, flushing batches to the database every [flushPeriodMs]. */
class RideRecorder(
    private val rides: RideDao,
    private val samples: SampleDao,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val flushPeriodMs: Long = 5_000L,
) : RideLog {
    private val lock = Any()
    private var rideId: Long? = null
    private var startedAt = 0L
    private var lastSec = -1
    private var buffer = ArrayList<SampleEntity>()
    private val aggregator = LiveAggregator()
    private var flusher: Job? = null

    override suspend fun begin(workoutId: String?, ftp: Int?): Long {
        check(synchronized(lock) { rideId } == null) { "a ride is already being recorded" }
        val started = clock()
        val id = rides.insert(RideEntity(startedAt = started, workoutId = workoutId, ftp = ftp))
        synchronized(lock) { rideId = id; startedAt = started; lastSec = -1; buffer = ArrayList(); aggregator.reset() }
        flusher = scope.launch {
            while (isActive) { delay(flushPeriodMs); flush() }
        }
        return id
    }

    override fun offer(frame: RideFrame) {
        synchronized(lock) {
            val id = rideId ?: return
            val sec = ((clock() - startedAt) / 1000).toInt()
            if (sec < 0 || sec <= lastSec) return
            lastSec = sec
            aggregator.add(frame)
            buffer += SampleEntity(id, sec, frame.powerWatts, frame.cadenceRpm, frame.resistancePercent, frame.heartRateBpm, frame.zone)
        }
    }

    /** Writes buffered samples; on failure the batch is kept (in order) for the next flush. */
    suspend fun flush() {
        val batch = synchronized(lock) { if (buffer.isEmpty()) return; buffer.also { buffer = ArrayList() } }
        try {
            samples.insertAll(batch)
        } catch (e: CancellationException) {
            synchronized(lock) { buffer.addAll(0, batch) }
            throw e
        } catch (e: Exception) {
            Log.w("PedalMate", "sample flush failed, will retry", e)
            synchronized(lock) { buffer.addAll(0, batch) }
        }
    }

    override suspend fun finish(): LiveAggregates? {
        val id = synchronized(lock) { rideId } ?: return null
        flusher?.cancelAndJoin(); flusher = null
        flush()
        val agg = synchronized(lock) { aggregator.snapshot() }
        try {
            rides.finish(id, clock(), agg.sampleCount, agg.avgPowerWatts, agg.maxPowerWatts, agg.avgCadenceRpm, agg.avgResistancePercent, agg.avgHeartRateBpm)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PedalMate", "ride finish failed; will be finalised on next start", e)
        }
        synchronized(lock) { rideId = null }
        return agg
    }
}

package dev.pedalmate.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun `samples round trip with nulls preserved`() = runBlocking {
        val id = db.rideDao().insert(RideEntity(startedAt = 1L))
        val written = listOf(
            SampleEntity(id, 0, 100, 80, 30, 120, 2),
            SampleEntity(id, 1, null, null, null, null, null),
            SampleEntity(id, 2, 150, null, 31, null, 3),
        )
        db.sampleDao().insertAll(written)
        assertEquals(written, db.sampleDao().forRide(id))
    }

    @Test fun `samples come back sorted by tSec`() = runBlocking {
        val id = db.rideDao().insert(RideEntity(startedAt = 1L))
        db.sampleDao().insertAll(listOf(2, 0, 1).map { SampleEntity(id, it, it, null, null, null, null) })
        assertEquals(listOf(0, 1, 2), db.sampleDao().forRide(id).map { it.tSec })
    }

    @Test fun `same ride and second replaces`() = runBlocking {
        val id = db.rideDao().insert(RideEntity(startedAt = 1L))
        db.sampleDao().insertAll(listOf(SampleEntity(id, 5, 100, null, null, null, null)))
        db.sampleDao().insertAll(listOf(SampleEntity(id, 5, 200, null, null, null, null)))
        assertEquals(1, db.sampleDao().count(id))
        assertEquals(200, db.sampleDao().forRide(id).single().powerWatts)
    }

    @Test fun `finish removes the ride from unfinished and stores aggregates`() = runBlocking {
        val id = db.rideDao().insert(RideEntity(startedAt = 1L))
        assertEquals(listOf(id), db.rideDao().unfinished().map { it.id })
        db.rideDao().finish(id, 9_000L, 10, 150, 300, 80, 30, 120)
        assertTrue(db.rideDao().unfinished().isEmpty())
        val row = db.rideDao().byId(id)!!
        assertEquals(9_000L, row.endedAt); assertEquals(10, row.sampleCount)
        assertEquals(150, row.avgPowerWatts); assertEquals(300, row.maxPowerWatts)
        assertEquals(80, row.avgCadenceRpm); assertEquals(30, row.avgResistancePercent)
        assertEquals(120, row.avgHeartRateBpm)
    }

    @Test fun `sample for a missing ride violates the foreign key`() {
        try {
            runBlocking { db.sampleDao().insertAll(listOf(SampleEntity(999, 0, 1, null, null, null, null))) }
            fail("expected SQLiteConstraintException")
        } catch (_: SQLiteConstraintException) { }
    }
}

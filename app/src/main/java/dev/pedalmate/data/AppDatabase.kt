package dev.pedalmate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Rides and per-second samples. Phase A has no migrations; Phase B must add a proper one. */
@Database(entities = [RideEntity::class, SampleEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun sampleDao(): SampleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pedalmate.db").build()
    }
}

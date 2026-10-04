package dev.pedalmate.sensor

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope

object SensorFactory {
    const val FLAVOR = "real"

    /** Real Bike+ source behind the supervisor. [scope] hosts the supervisor's 250 ms tick. */
    fun createBikeSource(context: Context, scope: CoroutineScope): BoundBikeDataSource =
        BikeSourceSupervisor(
            source = PelotonBikeInterfaceDataSource(context.applicationContext),
            clock = { SystemClock.elapsedRealtime() },
            scheduler = CoroutineTickScheduler(scope),
        )
}

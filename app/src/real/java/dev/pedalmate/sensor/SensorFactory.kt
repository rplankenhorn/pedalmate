package dev.pedalmate.sensor

import android.content.Context
import android.os.SystemClock
import dev.pedalmate.heartrate.AndroidBleScanner
import dev.pedalmate.heartrate.BleHeartRateDataSource
import dev.pedalmate.heartrate.BleScanner
import dev.pedalmate.heartrate.HeartRateConnector
import dev.pedalmate.heartrate.ManagedHeartRateDataSource
import dev.pedalmate.heartrate.SavedHrDeviceStore
import kotlinx.coroutines.CoroutineScope

object SensorFactory {
    const val FLAVOR = "real"

    /**
     * Real Bike+ source behind the supervisor. [scope] hosts the supervisor's 250 ms tick and must run on
     * Dispatchers.Default: the 1 Hz pollBikeData() is a blocking binder call on its dispatcher.
     */
    fun createBikeSource(context: Context, scope: CoroutineScope): BoundBikeDataSource =
        BikeSourceSupervisor(
            source = PelotonBikeInterfaceDataSource(context.applicationContext),
            clock = { SystemClock.elapsedRealtime() },
            scheduler = CoroutineTickScheduler(scope),
        )

    fun createHeartRateSource(context: Context, scope: CoroutineScope, store: SavedHrDeviceStore): ManagedHeartRateDataSource =
        HeartRateConnector(
            scanner = AndroidBleScanner(context.applicationContext),
            linkFactory = { address -> BleHeartRateDataSource(context.applicationContext, address) },
            store = store,
            clock = { SystemClock.elapsedRealtime() },
            scheduler = CoroutineTickScheduler(scope),
        )

    fun createBleScanner(context: Context): BleScanner = AndroidBleScanner(context.applicationContext)
}

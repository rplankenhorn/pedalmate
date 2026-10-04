package dev.pedalmate.sensor

import android.content.Context
import dev.pedalmate.heartrate.BleScanner
import dev.pedalmate.heartrate.ManagedHeartRateDataSource
import dev.pedalmate.heartrate.MockBleScanner
import dev.pedalmate.heartrate.MockHeartRateDataSource
import dev.pedalmate.heartrate.SavedHrDeviceStore
import kotlinx.coroutines.CoroutineScope

object SensorFactory {
    const val FLAVOR = "mock"

    /** [scope] must run on Dispatchers.Default: the supervisor's 1 Hz pollBikeData() is a blocking binder call on its dispatcher. */
    fun createBikeSource(context: Context, scope: CoroutineScope): BoundBikeDataSource =
        MockBikeDataSource(ScriptedProfile(seed = 1L, ftp = 200), scope)

    fun createHeartRateSource(context: Context, scope: CoroutineScope, store: SavedHrDeviceStore): ManagedHeartRateDataSource =
        MockHeartRateDataSource(scope)

    fun createBleScanner(context: Context): BleScanner = MockBleScanner()
}

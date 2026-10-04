package dev.pedalmate.sensor

import android.content.Context
import kotlinx.coroutines.CoroutineScope

object SensorFactory {
    const val FLAVOR = "mock"

    fun createBikeSource(context: Context, scope: CoroutineScope): BoundBikeDataSource =
        MockBikeDataSource(ScriptedProfile(seed = 1L, ftp = 200), scope)
}

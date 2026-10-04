package dev.pedalmate.data

import android.content.Context
import android.util.Log
import dev.pedalmate.audio.CuePlayer
import dev.pedalmate.heartrate.HrPairing
import dev.pedalmate.heartrate.SavedHrDeviceStore
import dev.pedalmate.PedalMateApp
import dev.pedalmate.ride.RideFinalizer
import dev.pedalmate.ride.RideRecorder
import dev.pedalmate.ride.RideSession
import dev.pedalmate.ride.SensorHub
import dev.pedalmate.sensor.SensorFactory
import dev.pedalmate.workout.WorkoutRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI: app-scoped singletons. [scope] runs on Dispatchers.Default, as the bike supervisor's blocking poll requires. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings: SettingsStore = SettingsStore.create(appContext)
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val workouts = WorkoutRepository(ContextAssetReader(appContext.assets))
    val hrStore: SavedHrDeviceStore = SettingsHrDeviceStore(settings, scope)
    val hub = SensorHub(
        bike = SensorFactory.createBikeSource(appContext, scope),
        hr = SensorFactory.createHeartRateSource(appContext, scope, hrStore),
    )
    val hrPairing = HrPairing(hub.hr, hrStore)
    val cuePlayer = CuePlayer(scope)

    @Volatile private var currentFtp: Int? = null
    private val ftpLoaded = CompletableDeferred<Unit>()
    private val finalizer = scope.launch(Dispatchers.IO) {
        try {
            RideFinalizer(database.rideDao(), database.sampleDao()).finalizeUnfinished()
        } catch (e: Exception) {
            Log.w("PedalMate", "finalizing unfinished rides failed", e)
        }
    }

    init {
        scope.launch { settings.settings.collect { currentFtp = it.ftp; ftpLoaded.complete(Unit) } }
    }

    val session = RideSession(
        hub = hub, workouts = workouts,
        rideLog = RideRecorder(database.rideDao(), database.sampleDao(), { System.currentTimeMillis() }, scope),
        cues = cuePlayer, ftpProvider = { currentFtp }, scope = scope,
        ready = { finalizer.join(); ftpLoaded.await() },
    )
}

val Context.appContainer: AppContainer get() = (applicationContext as PedalMateApp).container

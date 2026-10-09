package dev.pedalmate.ride

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.pedalmate.R
import dev.pedalmate.data.appContainer
import dev.pedalmate.overlay.IntervalToast
import dev.pedalmate.overlay.OverlayContent
import dev.pedalmate.overlay.OverlayController
import dev.pedalmate.overlay.OverlayPolicy
import dev.pedalmate.overlay.OverlayPrefs
import dev.pedalmate.overlay.OverlayPrefsState
import dev.pedalmate.overlay.OverlayToastWindow
import dev.pedalmate.overlay.OverlayUiModel
import dev.pedalmate.overlay.ShowResult
import dev.pedalmate.overlay.ToastModel
import dev.pedalmate.ui.setup.SetupActivity
import dev.pedalmate.workout.WorkoutEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that drives the app-scoped [RideSession] from a 250 ms elapsed-realtime ticker.
 * The session itself lives in the app container, so destroying the service does not end the ride.
 */
class RideService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private lateinit var overlay: OverlayController
    private lateinit var prefs: OverlayPrefs
    private var minimized by mutableStateOf(false)
    private lateinit var toast: IntervalToast
    private var overlayJob: Job? = null
    private val container get() = appContainer
    private var lastStartId = 0
    private var lastRideStartId = 0               // id of the newest START command; main thread only
    private var pendingStop: Job? = null          // the newest stop; main thread only. A START waits for it before beginning

    /** How long a finished ride keeps the panel up before the service stops itself; tests shorten it. */
    @VisibleForTesting internal var finishedLingerMs: Long = FINISHED_LINGER_MS

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ride", NotificationManager.IMPORTANCE_LOW),
        )
        overlay = OverlayController(applicationContext, getSystemService(WindowManager::class.java))
        prefs = OverlayPrefs(container.settings)
        toast = IntervalToast(uiScope, OverlayToastWindow(applicationContext, getSystemService(WindowManager::class.java)))
        uiScope.launch {                          // subscribe before any ride starts: events have no replay
            container.session.events.filterIsInstance<WorkoutEvent.IntervalChanged>()
                .collect { toast.show(ToastModel.from(it)) }
        }
        uiScope.launch {
            container.session.snapshot.map { it.status == RideStatus.IDLE }.distinctUntilChanged()
                .collect { idle -> if (idle) toast.cancel() }
        }
        uiScope.launch {                          // a finished ride lingers, then ends exactly as STOP would
            container.session.snapshot.map { it.status }.distinctUntilChanged().collectLatest { status ->
                if (status != RideStatus.FINISHED) return@collectLatest
                // keepScreenOn is false during the linger; if the bike deep-sleeps this delay fires on wake (accepted).
                delay(finishedLingerMs)
                if (container.session.snapshot.value.status != RideStatus.FINISHED) return@collectLatest
                Log.i("PedalMate", "ride finished ${finishedLingerMs} ms ago, stopping the service")
                stopRide(lastStartId)
            }
        }
        overlayJob = uiScope.launch {
            minimized = prefs.load().minimized
            container.session.snapshot
                .map { OverlayPolicy.directive(it.status) }
                .distinctUntilChanged()
                .collect { d ->
                    overlay.setKeepScreenOn(d.keepScreenOn)
                    if (!d.visible) {
                        overlay.hide()
                        return@collect
                    }
                    showOverlay(prefs.load())      // latest saved position, not the one read at service creation
                }
        }
    }

    private fun showOverlay(saved: OverlayPrefsState) {
        val result = overlay.show(
            content = {
                val model by remember { container.session.snapshot.map { OverlayUiModel.from(it) } }
                    .collectAsState(initial = OverlayUiModel.from(container.session.snapshot.value))
                OverlayContent(model, minimized) { toggleMinimized() }
            },
            initial = saved.placement,
            onMoved = { p -> container.scope.launch { prefs.setPlacement(p) } },
        )
        if (result == ShowResult.NO_PERMISSION || result == ShowResult.FAILED) {
            Log.w("PedalMate", "overlay not shown: $result")
        }
    }

    /** The rider may have granted the overlay permission mid-ride: show the panel if it is wanted but missing. */
    private fun refreshOverlay() {
        uiScope.launch {
            if (OverlayPolicy.needsShow(container.session.snapshot.value.status, overlay.isShowing)) {
                showOverlay(prefs.load())
            }
        }
    }

    private fun toggleMinimized() {
        minimized = !minimized
        val m = minimized
        container.scope.launch { prefs.setMinimized(m) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        enterForeground()                         // the foreground contract comes first, whatever we decide next
        val session = container.session
        when (RideServicePolicy.decide(intent?.action, intent == null, session.isActive)) {
            ServiceAction.START -> { lastRideStartId = startId; startRide(intent?.getStringExtra(EXTRA_WORKOUT_ID), startId) }
            ServiceAction.CONTINUE -> ensureTicker()
            ServiceAction.REFRESH_OVERLAY -> { ensureTicker(); refreshOverlay() }
            ServiceAction.STOP, ServiceAction.STOP_SELF -> stopRide(startId)
        }
        return START_STICKY
    }

    private fun enterForeground() {
        val stop = PendingIntent.getService(this, 0, stopIntent(this), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, SetupActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("PedalMate ride in progress")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        try {
            startForeground(NOTIFICATION_ID, n, types)
        } catch (e: SecurityException) {
            Log.w("PedalMate", "typed startForeground denied, falling back to untyped", e)
            startForeground(NOTIFICATION_ID, n)
        } catch (e: IllegalArgumentException) {
            Log.w("PedalMate", "typed startForeground rejected, falling back to untyped", e)
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun startRide(workoutId: String?, startId: Int) {
        val priorStop = pendingStop               // captured on main, so only a stop issued before this START is awaited
        scope.launch {
            priorStop?.join()                     // STOP then START: let the finish land, then begin the new ride recorded
            val s = container.session
            val result = if (workoutId == null) s.startFreeRide() else s.startWorkout(workoutId)
            Log.i("PedalMate", "start ride workout=$workoutId result=$result")
            when (result) {
                StartResult.UnknownWorkout -> withContext(Dispatchers.Main.immediate) { stopRide(startId) }   // pendingStop is main-only
                StartResult.Stopped -> withContext(Dispatchers.Main.immediate) { stopServiceIfIdle(startId) }   // a STOP beat us; its own stopSelf may have lost to this start's id
                else -> withContext(Dispatchers.Main.immediate) { ensureTicker() }
            }
        }
    }

    /**
     * Ends the ride, then the service, but only if no later START is queued. A later REFRESH_OVERLAY or CONTINUE
     * (delivered while the session was still active) must not keep the idle service alive, so the stop covers every
     * command up to the newest one; a newer START is protected because [stopSelfResult] with this command's
     * [startId] is a no-op when a newer start is queued.
     */
    private fun stopRide(startId: Int) {          // main thread only (writes pendingStop)
        pendingStop = container.scope.launch {                  // app scope: survives this service being destroyed
            withContext(NonCancellable) { container.session.stop() }
            withContext(Dispatchers.Main.immediate) { stopServiceIfIdle(startId) }
        }
    }

    /** Main thread only. Stops the service unless the session is active or a START newer than [startId] is queued. */
    private fun stopServiceIfIdle(startId: Int) {
        val upTo = if (lastRideStartId > startId) startId else lastStartId
        if (!container.session.isActive && stopSelfResult(upTo)) {
            ticker?.cancel()
            ticker = null
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    /** Main-thread only: [ticker] is confined to the main thread so the check-then-launch cannot race. */
    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var n = 0
            while (isActive) {
                container.session.tick(SystemClock.elapsedRealtime())
                if (++n % 20 == 0) {              // every 5 s: one line, never per frame
                    val s = container.session.snapshot.value
                    Log.i(
                        "PedalMate",
                        "ride status=${s.status} step=${s.workout?.stepIndex} elapsed=${s.elapsedMs / 1000}s " +
                            "bike=${s.bikeState} watts=${s.smoothedPowerWatts} zone=${s.currentZone} target=${s.targetStatus}",
                    )
                }
                delay(TICK_MS)
            }
        }
    }

    override fun onDestroy() {
        overlay.hide()                            // the window must not leak with the service
        overlayJob?.cancel()
        toast.cancel()
        uiScope.cancel()
        scope.cancel()                            // ticker only; the session lives on in AppContainer
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.pedalmate.action.START_RIDE"
        const val ACTION_STOP = "dev.pedalmate.action.STOP_RIDE"
        const val ACTION_REFRESH_OVERLAY = "dev.pedalmate.action.REFRESH_OVERLAY"
        const val EXTRA_WORKOUT_ID = "workout_id"
        private const val CHANNEL_ID = "ride"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 250L
        const val FINISHED_LINGER_MS = 120_000L

        /** Starts a workout ride, or a free ride when [workoutId] is null. */
        fun start(context: Context, workoutId: String?) {
            val i = Intent(context, RideService::class.java).setAction(ACTION_START)
            if (workoutId != null) i.putExtra(EXTRA_WORKOUT_ID, workoutId)
            ContextCompat.startForegroundService(context, i)
        }

        fun refreshOverlayIntent(context: Context): Intent = Intent(context, RideService::class.java).setAction(ACTION_REFRESH_OVERLAY)

        fun stopIntent(context: Context): Intent = Intent(context, RideService::class.java).setAction(ACTION_STOP)
    }
}

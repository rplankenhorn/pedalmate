package dev.pedalmate.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pedalmate.data.AppContainer
import dev.pedalmate.data.Settings
import dev.pedalmate.data.SettingsStore
import dev.pedalmate.overlay.formatRange
import dev.pedalmate.ride.RideSnapshot
import dev.pedalmate.ride.RideStatus
import dev.pedalmate.workout.WorkoutRepository
import dev.pedalmate.workout.ZoneTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Selection id meaning "free ride, no workout". */
const val FREE_RIDE_ID = "@free"

private const val LICHESS_MISSING =
    "Lichess not found: run adb shell pm list packages | grep lichess and enter the package under Advanced."

/** One row of the zone preview, e.g. zone 5 and `263-299 W`. */
data class ZoneRow(val zone: Int, val rangeText: String)

/** One selectable entry in the workout picker. */
data class WorkoutChoice(val id: String, val title: String, val subtitle: String)

/** Everything the setup screen renders. */
data class SetupUiState(
    val loaded: Boolean,
    val ftpText: String,
    val ftpError: String?,
    val zoneRows: List<ZoneRow>,
    val workouts: List<WorkoutChoice>,
    val loadErrors: List<String>,
    val selectedId: String,
    val lichessPackage: String,
    val lichessPackageError: String?,
    val rideStatus: RideStatus,
    val canStart: Boolean,
    val startBlockedReason: String?,
    val startWarning: String?,
    val hrDeviceLabel: String?,
    val message: String?,
)

/** Outcome of [SetupViewModel.start]. */
sealed interface StartDecision {
    data class Started(val lichessPackage: String?) : StartDecision
    data class Blocked(val reason: String) : StartDecision
}

/** Holds all setup-screen decisions: FTP validation, workout selection, start gating, Lichess resolution. */
class SetupViewModel(
    private val settings: SettingsStore,
    workouts: WorkoutRepository,
    snapshot: StateFlow<RideSnapshot>,
    private val commands: RideCommands,
    private val isInstalled: (String) -> Boolean,
) : ViewModel() {
    private data class Local(
        val ftpEdit: String? = null,
        val selected: String? = null,
        val pkgEdit: String? = null,
        val message: String? = null,
    )

    private val local = MutableStateFlow(Local())
    private val loadResult = workouts.load()
    private val choices: List<WorkoutChoice> = loadResult.workouts.map {
        val minutes = it.totalSeconds / 60
        WorkoutChoice(it.id, it.name, if (it.description.isNullOrBlank()) "$minutes min" else "$minutes min - ${it.description}")
    } + WorkoutChoice(FREE_RIDE_ID, "Free ride", "No target, just ride")
    private val loadErrors = loadResult.errors.map { "${it.key}: ${it.value}" }

    val state: StateFlow<SetupUiState> = combine(settings.settings, local, snapshot) { s, l, snap -> build(s, l, snap) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(null, Local(), snapshot.value))

    private fun build(s: Settings?, l: Local, snap: RideSnapshot): SetupUiState {
        val ftpText = l.ftpEdit ?: s?.ftp?.toString().orEmpty()
        val parse = FtpInput.parse(ftpText)
        val ftpError = (parse as? FtpParse.Invalid)?.reason
        val rows = ZoneTable.forFtp((parse as? FtpParse.Valid)?.ftp)?.ranges.orEmpty().map {
            ZoneRow(it.zone.number, formatRange(it).removePrefix("Z${it.zone.number} "))
        }
        val active = snap.status == RideStatus.RUNNING || snap.status == RideStatus.PAUSED
        val blocked = when {
            s == null -> "Loading settings"
            ftpError != null -> "Fix the FTP value first"
            active -> "A ride is already running"
            else -> null
        }
        val pkgText = l.pkgEdit ?: s?.lichessPackage ?: SettingsStore.DEFAULT_LICHESS_PACKAGE
        val hrLabel = s?.let { st -> st.hrAddress?.let { addr -> st.hrName?.let { "$it ($addr)" } ?: addr } }
        return SetupUiState(
            loaded = s != null, ftpText = ftpText, ftpError = ftpError, zoneRows = rows,
            workouts = choices, loadErrors = loadErrors, selectedId = resolveSelected(s, l),
            lichessPackage = pkgText,
            lichessPackageError = if (l.pkgEdit != null && l.pkgEdit.isBlank()) "Package name required" else null,
            rideStatus = snap.status, canStart = blocked == null, startBlockedReason = blocked,
            startWarning = if (parse is FtpParse.Empty) "No FTP set: zones and targets will show dashes." else null,
            hrDeviceLabel = hrLabel, message = l.message,
        )
    }

    private fun resolveSelected(s: Settings?, l: Local): String {
        val ids = choices.map { it.id }.toSet()
        l.selected?.takeIf { it in ids }?.let { return it }
        s?.lastWorkoutId?.takeIf { it in ids }?.let { return it }
        return loadResult.workouts.firstOrNull()?.id ?: FREE_RIDE_ID
    }

    fun onFtpTextChanged(text: String) {
        local.update { it.copy(ftpEdit = text) }
        when (val p = FtpInput.parse(text)) {
            is FtpParse.Valid -> viewModelScope.launch { settings.setFtp(p.ftp) }
            FtpParse.Empty -> viewModelScope.launch { settings.setFtp(null) }
            is FtpParse.Invalid -> Unit // never saved: the previous value stays stored
        }
    }

    fun select(id: String) {
        if (choices.none { it.id == id }) return
        local.update { it.copy(selected = id) }
        viewModelScope.launch { settings.setLastWorkoutId(id) }
    }

    fun onLichessPackageChanged(text: String) {
        local.update { it.copy(pkgEdit = text) }
        if (text.isNotBlank()) viewModelScope.launch { settings.setLichessPackage(text.trim()) }
    }

    /** Starts the selected ride unless blocked; the caller launches Lichess when the result carries a package. */
    suspend fun start(): StartDecision {
        val s = state.value
        s.startBlockedReason?.let { reason -> say(reason); return StartDecision.Blocked(reason) }
        val id = s.selectedId
        commands.start(if (id == FREE_RIDE_ID) null else id)
        viewModelScope.launch { settings.setLastWorkoutId(id) }
        val pkg = LichessLauncher.resolve(settings.settings.first().lichessPackage, isInstalled)
        if (pkg == null) say(LICHESS_MISSING)
        return StartDecision.Started(pkg)
    }

    /** The package to launch for the standalone LAUNCH LICHESS button, or null (with a message) when not installed. */
    suspend fun onLaunchLichessRequested(): String? {
        val pkg = LichessLauncher.resolve(settings.settings.first().lichessPackage, isInstalled)
        if (pkg == null) say(LICHESS_MISSING)
        return pkg
    }

    fun stop() = commands.stop()
    fun pause() = commands.pause()
    fun resume() = commands.resume()
    fun skip() = commands.skip()
    fun dismissMessage() = say(null)
    fun say(m: String?) = local.update { it.copy(message = m) }
}

/** Builds [SetupViewModel] from the app container. */
class SetupViewModelFactory(
    private val container: AppContainer,
    private val commands: RideCommands,
    private val isInstalled: (String) -> Boolean,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SetupViewModel(container.settings, container.workouts, container.session.snapshot, commands, isInstalled) as T
    }
}

package dev.pedalmate.ui.setup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.pedalmate.data.appContainer
import dev.pedalmate.heartrate.HrPairingController
import dev.pedalmate.heartrate.requiredBlePermissions
import dev.pedalmate.sensor.SensorFactory
import dev.pedalmate.ui.debug.CueDebugRow
import dev.pedalmate.ui.debug.SensorDebugScreen
import dev.pedalmate.ui.theme.PedalMateTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Launcher activity: hosts the setup screen and the sensor diagnostics, and holds the sensors while visible. */
class SetupActivity : ComponentActivity() {
    private val vm: SetupViewModel by viewModels {
        SetupViewModelFactory(appContainer, ServiceRideCommands(applicationContext, appContainer.session)) { pkg ->
            applicationContext.packageManager.getLaunchIntentForPackage(pkg) != null
        }
    }
    private var overlayGranted by mutableStateOf(false)
    private var diagnostics by mutableStateOf(false)
    private val hrPairing by lazy { HrPairingController(SensorFactory.createBleScanner(applicationContext), appContainer.hrPairing, lifecycleScope) }
    private val blePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) hrPairing.startScan() else vm.showMessage(
            "Bluetooth scan needs the Location permission. Run: adb shell pm grant $packageName android.permission.ACCESS_FINE_LOCATION",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = appContainer
        val actions = SetupActions(
            onFtpChange = vm::onFtpTextChanged,
            onSelect = vm::select,
            onStart = {
                lifecycleScope.launch {
                    val d = vm.start()
                    if (d is StartDecision.Started && d.lichessPackage != null) launchLichess(d.lichessPackage)
                }
            },
            onStop = vm::stop,
            onPause = vm::pause,
            onResume = vm::resume,
            onSkip = vm::skip,
            onLaunchLichess = { lifecycleScope.launch { vm.onLaunchLichessRequested()?.let(::launchLichess) } },
            onGrantOverlay = ::grantOverlay,
            onPackageChange = vm::onLichessPackageChanged,
            onDismissMessage = vm::dismissMessage,
            onShowDiagnostics = { diagnostics = true },
            onPairHr = ::pairHr,
            onScanHr = { hrPairing.startScan() },
            onPickStrap = hrPairing::select,
            onForgetHr = hrPairing::forget,
            onClosePairing = hrPairing::stopScan,
        )
        setContent {
            PedalMateTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                if (diagnostics) {
                    BackHandler { diagnostics = false }
                    val m by c.hub.bike.metrics.collectAsStateWithLifecycle()
                    val st by c.hub.bike.connectionState.collectAsStateWithLifecycle()
                    SensorDebugScreen(m, st) {
                        CueDebugRow(
                            onPlay = c.cuePlayer::play,
                            onPlayDelayed = { cue -> lifecycleScope.launch { delay(5_000); c.cuePlayer.play(cue) } },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { diagnostics = false }) { Text("BACK") }
                        }
                    }
                } else {
                    val bpm by c.hub.hr.bpm.collectAsStateWithLifecycle()
                    val hrState by c.hub.hr.connectionState.collectAsStateWithLifecycle()
                    val pairing by hrPairing.state.collectAsStateWithLifecycle()
                    SetupScreen(state, HrCardState(state.hrDeviceLabel, hrState, bpm), pairing, overlayGranted, actions)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appContainer.hub.acquire()
    }

    override fun onStop() {
        hrPairing.stopScan()
        appContainer.hub.release()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = Settings.canDrawOverlays(this)
    }

    private fun pairHr() {
        val missing = requiredBlePermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) hrPairing.startScan() else blePermissionLauncher.launch(missing.toTypedArray())
    }

    private fun launchLichess(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Minimal grant flow; A15 replaces it with PermissionHelper. */
    private fun grantOverlay() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            vm.showMessage("Run: adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow")
        }
    }
}

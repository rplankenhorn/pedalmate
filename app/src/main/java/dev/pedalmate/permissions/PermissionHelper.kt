package dev.pedalmate.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings

/** Reads the live permission state and builds the settings intents; decisions live in [PermissionPlanner]. */
class PermissionHelper(private val context: Context) {
    private val prefs = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)

    fun overlayIntent(): Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun locationSettingsIntent(): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    /** Remembers that a runtime request was shown, so a second denial falls through to the adb command. */
    fun markLocationRequested() {
        prefs.edit().putBoolean(KEY, true).apply()
    }

    private fun canResolve(i: Intent) = i.resolveActivity(context.packageManager) != null

    /** Overlay, location, location services, in that order. */
    fun items(): List<PermissionItem> {
        val pkg = context.packageName
        val lm = context.getSystemService(LocationManager::class.java)
        return listOf(
            PermissionPlanner.overlay(Settings.canDrawOverlays(context), canResolve(overlayIntent()), pkg),
            PermissionPlanner.location(
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
                prefs.getBoolean(KEY, false), pkg,
            ),
            PermissionPlanner.locationServices(lm?.isLocationEnabled ?: false, canResolve(locationSettingsIntent())),
        )
    }

    private companion object {
        const val KEY = "location_requested"
    }
}

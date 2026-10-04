package dev.pedalmate.permissions

import android.Manifest
import android.provider.Settings

/** The permissions PedalMate can use; none of them blocks a ride. */
enum class PermissionKind { OVERLAY, LOCATION, LOCATION_SERVICES }

/** How the rider can fix a missing permission. */
sealed interface Fix {
    data object None : Fix
    data class OpenScreen(val intentAction: String) : Fix
    data class RequestRuntime(val permission: String) : Fix
    data class ShowAdb(val command: String) : Fix
}

/** One row of the permissions card. */
data class PermissionItem(val kind: PermissionKind, val title: String, val granted: Boolean, val fix: Fix)

/** Pure decisions about which fix to offer; [PermissionHelper] feeds it the live state. */
object PermissionPlanner {
    private const val OVERLAY_TITLE = "Draw over other apps"
    private const val LOCATION_TITLE = "Location (needed for Bluetooth scan)"
    private const val SERVICES_TITLE = "Location services (needed for Bluetooth scan)"

    /** Shown instead of scanning when the Location items are not both granted. */
    const val HR_SCAN_BLOCKED_MESSAGE = "Fix the Location items under Permissions first"

    fun overlay(granted: Boolean, canResolveSettings: Boolean, pkg: String): PermissionItem {
        val fix = when {
            granted -> Fix.None
            canResolveSettings -> Fix.OpenScreen(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            else -> Fix.ShowAdb("adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow")
        }
        return PermissionItem(PermissionKind.OVERLAY, OVERLAY_TITLE, granted, fix)
    }

    fun location(granted: Boolean, requestedBefore: Boolean, pkg: String): PermissionItem {
        val fix = when {
            granted -> Fix.None
            !requestedBefore -> Fix.RequestRuntime(Manifest.permission.ACCESS_FINE_LOCATION)
            else -> Fix.ShowAdb("adb shell pm grant $pkg android.permission.ACCESS_FINE_LOCATION")
        }
        return PermissionItem(PermissionKind.LOCATION, LOCATION_TITLE, granted, fix)
    }

    fun locationServices(enabled: Boolean, canResolveSettings: Boolean): PermissionItem {
        val fix = when {
            enabled -> Fix.None
            canResolveSettings -> Fix.OpenScreen(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            else -> Fix.ShowAdb("adb shell settings put secure location_mode 3")
        }
        return PermissionItem(PermissionKind.LOCATION_SERVICES, SERVICES_TITLE, enabled, fix)
    }

    private fun granted(items: List<PermissionItem>, kind: PermissionKind) =
        items.any { it.kind == kind && it.granted }

    /** True only when the location permission AND the system Location switch are both on (BLE scans return nothing otherwise). */
    fun hrScanReady(items: List<PermissionItem>): Boolean =
        granted(items, PermissionKind.LOCATION) && granted(items, PermissionKind.LOCATION_SERVICES)

    fun overlayReady(items: List<PermissionItem>): Boolean = granted(items, PermissionKind.OVERLAY)

    /** The first Location item that stops a scan, or null when scanning is ready. */
    fun firstBlockingItem(items: List<PermissionItem>): PermissionItem? =
        items.firstOrNull { (it.kind == PermissionKind.LOCATION || it.kind == PermissionKind.LOCATION_SERVICES) && !it.granted }

    /** A cancelled or interrupted request returns an empty map, which must not count as granted. */
    fun blePermissionsGranted(result: Map<String, Boolean>): Boolean = result.isNotEmpty() && result.values.all { it }
}

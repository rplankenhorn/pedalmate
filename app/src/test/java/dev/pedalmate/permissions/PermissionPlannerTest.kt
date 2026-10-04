package dev.pedalmate.permissions

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPlannerTest {
    private val pkg = "dev.pedalmate"
    private val loc = "android.permission.ACCESS_FINE_LOCATION"

    @Test fun overlayGranted() {
        assertEquals(PermissionItem(PermissionKind.OVERLAY, "Draw over other apps", true, Fix.None), PermissionPlanner.overlay(true, true, pkg))
    }

    @Test fun overlayResolvable() {
        val i = PermissionPlanner.overlay(false, true, pkg)
        assertFalse(i.granted)
        assertEquals(Fix.OpenScreen(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), i.fix)
        assertEquals("Draw over other apps", i.title)
    }

    @Test fun overlayUnresolvable() {
        assertEquals(Fix.ShowAdb("adb shell appops set dev.pedalmate SYSTEM_ALERT_WINDOW allow"), PermissionPlanner.overlay(false, false, pkg).fix)
    }

    @Test fun locationGranted() {
        val i = PermissionPlanner.location(true, false, pkg)
        assertTrue(i.granted)
        assertEquals(Fix.None, i.fix)
        assertEquals("Location (needed for Bluetooth scan)", i.title)
        assertEquals(PermissionKind.LOCATION, i.kind)
    }

    @Test fun locationNotRequested() {
        assertEquals(Fix.RequestRuntime(loc), PermissionPlanner.location(false, false, pkg).fix)
    }

    @Test fun locationRequestedBefore() {
        assertEquals(Fix.ShowAdb("adb shell pm grant dev.pedalmate android.permission.ACCESS_FINE_LOCATION"), PermissionPlanner.location(false, true, pkg).fix)
    }

    @Test fun locationServicesEnabled() {
        val i = PermissionPlanner.locationServices(true, true)
        assertTrue(i.granted)
        assertEquals(Fix.None, i.fix)
        assertEquals("Location services (needed for Bluetooth scan)", i.title)
        assertEquals(PermissionKind.LOCATION_SERVICES, i.kind)
    }

    @Test fun locationServicesDisabledResolvable() {
        assertEquals(Fix.OpenScreen(Settings.ACTION_LOCATION_SOURCE_SETTINGS), PermissionPlanner.locationServices(false, true).fix)
    }

    @Test fun locationServicesDisabledUnresolvable() {
        assertEquals(Fix.ShowAdb("adb shell settings put secure location_mode 3"), PermissionPlanner.locationServices(false, false).fix)
    }

    @Test fun packageIsInterpolated() {
        val mock = "dev.pedalmate.mock"
        assertTrue((PermissionPlanner.overlay(false, false, mock).fix as Fix.ShowAdb).command.contains("appops set dev.pedalmate.mock SYSTEM_ALERT_WINDOW allow"))
        assertTrue((PermissionPlanner.location(false, true, mock).fix as Fix.ShowAdb).command.contains("pm grant dev.pedalmate.mock android"))
    }

    private fun items(overlay: Boolean, location: Boolean, services: Boolean) = listOf(
        PermissionPlanner.overlay(overlay, true, pkg),
        PermissionPlanner.location(location, false, pkg),
        PermissionPlanner.locationServices(services, true),
    )

    @Test fun hrScanReadyTable() {
        assertTrue(PermissionPlanner.hrScanReady(items(false, true, true)))
        assertFalse(PermissionPlanner.hrScanReady(items(true, true, false)))   // API 29: permission granted, system Location OFF
        assertFalse(PermissionPlanner.hrScanReady(items(true, false, true)))
        assertFalse(PermissionPlanner.hrScanReady(items(true, false, false)))
        assertFalse(PermissionPlanner.hrScanReady(emptyList()))
    }

    @Test fun overlayReadyTable() {
        assertTrue(PermissionPlanner.overlayReady(items(true, false, false)))
        assertFalse(PermissionPlanner.overlayReady(items(false, true, true)))
    }

    @Test fun locationServicesOffMessageFollowsHrGate() {
        assertEquals(PermissionPlanner.HR_SCAN_BLOCKED_MESSAGE, "Fix the Location items under Permissions first")
        assertEquals(PermissionKind.LOCATION_SERVICES, PermissionPlanner.firstBlockingItem(items(true, true, false))?.kind)
        assertEquals(PermissionKind.LOCATION, PermissionPlanner.firstBlockingItem(items(true, false, false))?.kind)
        assertEquals(null, PermissionPlanner.firstBlockingItem(items(false, true, true)))
    }

    @Test fun bleResultEmptyIsNotGranted() {
        assertFalse(PermissionPlanner.blePermissionsGranted(emptyMap()))
        assertFalse(PermissionPlanner.blePermissionsGranted(mapOf(loc to true, "x" to false)))
        assertTrue(PermissionPlanner.blePermissionsGranted(mapOf(loc to true)))
    }
}

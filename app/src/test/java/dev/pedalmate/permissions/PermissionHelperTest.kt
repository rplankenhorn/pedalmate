package dev.pedalmate.permissions

import android.Manifest
import android.app.Application
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.location.LocationManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PermissionHelperTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val helper = PermissionHelper(app)

    private fun locationOn(on: Boolean) =
        shadowOf(app.getSystemService(LocationManager::class.java)).setLocationEnabled(on)

    @Suppress("DEPRECATION") // Robolectric 4.13 still offers only this registration for implicit intents
    private fun resolvable(i: android.content.Intent) {
        val ri = ResolveInfo().apply { activityInfo = ActivityInfo().apply { name = "X"; packageName = "p" } }
        shadowOf(app.packageManager).addResolveInfoForIntent(i, ri)
    }

    @Test fun allGranted() {
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        locationOn(true)
        val items = helper.items()
        assertTrue(items.all { it.granted })
        assertEquals(listOf(PermissionKind.OVERLAY, PermissionKind.LOCATION, PermissionKind.LOCATION_SERVICES), items.map { it.kind })
    }

    @Test fun overlayDeniedResolvable() {
        ShadowSettings.setCanDrawOverlays(false)
        resolvable(helper.overlayIntent())
        assertEquals(Fix.OpenScreen(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), helper.items()[0].fix)
    }

    @Test fun overlayDeniedUnresolvable() {
        ShadowSettings.setCanDrawOverlays(false)
        val fix = helper.items()[0].fix as Fix.ShowAdb
        assertTrue(fix.command.contains("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow"))
    }

    @Test fun locationDeniedThenRequested() {
        assertEquals(Fix.RequestRuntime(Manifest.permission.ACCESS_FINE_LOCATION), helper.items()[1].fix)
        helper.markLocationRequested()
        val fix = helper.items()[1].fix as Fix.ShowAdb
        assertTrue(fix.command.contains("pm grant ${app.packageName} android.permission.ACCESS_FINE_LOCATION"))
        val again = PermissionHelper(app).items()[1].fix
        assertTrue(again is Fix.ShowAdb)
    }

    @Test fun locationServicesOffUnresolvable() {
        locationOn(false)
        assertEquals(Fix.ShowAdb("adb shell settings put secure location_mode 3"), helper.items()[2].fix)
    }

    @Test fun locationPermissionGrantedButServicesOffIsNotScanReady() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        locationOn(false)
        val items = helper.items()
        assertTrue(items[1].granted)
        assertTrue(!PermissionPlanner.hrScanReady(items))
    }

    @Test fun intents() {
        val o = helper.overlayIntent()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, o.action)
        assertEquals("package:${app.packageName}", o.data.toString())
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, helper.locationSettingsIntent().action)
    }
}

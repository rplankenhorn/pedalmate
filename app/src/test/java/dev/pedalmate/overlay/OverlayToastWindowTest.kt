package dev.pedalmate.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import dev.pedalmate.testutil.FakeWindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayToastWindowTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val fake = FakeWindowManager()
    private var allowed = true
    private val window = OverlayToastWindow(ctx, fake.manager) { allowed }
    private val m1 = ToastModel("NEXT INTERVAL", "ZONE 5", "263\u2013299 W", "3:00")
    private val m2 = ToastModel("NEXT INTERVAL", "COOLDOWN", null, "5:00")

    @Test fun showUsesExactWindowParams() {
        window.show(m1)
        assertEquals(1, fake.added.size)
        val p = fake.added[0].params
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, p.type)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            p.flags,
        )
        assertEquals(PixelFormat.TRANSLUCENT, p.format)
        assertEquals(Gravity.CENTER, p.gravity)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, p.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, p.height)
        assertTrue(p.x == 0 && p.y == 0)
    }

    @Test fun secondShowReusesTheWindow() {
        window.show(m1)
        window.show(m2)
        assertEquals(1, fake.added.size)
        assertTrue(fake.removed.isEmpty())
    }

    @Test fun hideRemovesDestroysAndIsIdempotent() {
        window.show(m1)
        val view = fake.added[0].view
        window.hide()
        assertEquals(listOf(view), fake.removed)
        assertEquals(Lifecycle.State.DESTROYED, view.findViewTreeLifecycleOwner()!!.lifecycle.currentState)
        window.hide()
        assertEquals(1, fake.removed.size)
        window.show(m2)
        assertEquals(2, fake.added.size)
    }

    @Test fun noPermissionIsANoOp() {
        allowed = false
        window.show(m1)
        assertTrue(fake.added.isEmpty())
    }

    @Test fun addFailureDoesNotThrowAndIsRecoverable() {
        fake.addFailure = WindowManager.BadTokenException("x")
        window.show(m1)
        assertTrue(fake.added.isEmpty())
        fake.addFailure = null
        window.show(m1)
        assertEquals(1, fake.added.size)
    }

    @Test fun rootIsNotClickable() {
        window.show(m1)
        assertFalse(fake.added[0].view.isClickable)
        assertTrue(fake.added[0].params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
    }
}

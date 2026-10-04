package dev.pedalmate.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import dev.pedalmate.testutil.FakeWindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayControllerTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val fake = FakeWindowManager()
    private var allowed = true
    private val screen = 1920 to 1080
    private val controller = OverlayController(ctx, fake.manager, { allowed }, { screen })
    private val content: @Composable () -> Unit = { }
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val notFocusable = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    private val keepOn = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

    private fun lifecycleOf(v: View) = v.findViewTreeLifecycleOwner()!!.lifecycle.currentState
    private fun ev(action: Int, x: Float, y: Float) = MotionEvent.obtain(0L, 0L, action, x, y, 0)

    @Test
    fun showWithoutPermission() {
        allowed = false
        assertEquals(ShowResult.NO_PERMISSION, controller.show(content, null) {})
        assertTrue(fake.added.isEmpty())
        assertFalse(controller.isShowing)
    }

    @Test
    fun showSuccessUsesExactWindowParams() {
        assertEquals(ShowResult.SHOWN, controller.show(content, null) {})
        assertEquals(1, fake.added.size)
        val a = fake.added[0]
        val p = a.params
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, p.type)
        assertEquals(notFocusable, p.flags)
        assertEquals(PixelFormat.TRANSLUCENT, p.format)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, p.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, p.height)
        assertEquals(Gravity.TOP or Gravity.START, p.gravity)
        assertEquals(Lifecycle.State.RESUMED, lifecycleOf(a.view))
        assertTrue(controller.isShowing)
    }

    @Test
    fun secondShowIsAlreadyShown() {
        controller.show(content, null) {}
        assertEquals(ShowResult.ALREADY_SHOWN, controller.show(content, null) {})
        assertEquals(1, fake.added.size)
    }

    @Test
    fun addViewFailureIsFailedAndRecoverable() {
        fake.addFailure = WindowManager.BadTokenException("boom")
        assertEquals(ShowResult.FAILED, controller.show(content, null) {})
        assertFalse(controller.isShowing)
        fake.addFailure = null
        assertEquals(ShowResult.SHOWN, controller.show(content, null) {})
    }

    @Test
    fun securityExceptionIsFailed() {
        fake.addFailure = SecurityException("nope")
        assertEquals(ShowResult.FAILED, controller.show(content, null) {})
        assertFalse(controller.isShowing)
    }

    @Test
    fun hideRemovesAndDestroys() {
        controller.show(content, null) {}
        val view = fake.added[0].view
        controller.hide()
        assertEquals(listOf(view), fake.removed)
        assertEquals(Lifecycle.State.DESTROYED, lifecycleOf(view))
        assertFalse(controller.isShowing)
        controller.hide()
        assertEquals(1, fake.removed.size)
    }

    @Test
    fun hideSurvivesRemoveFailure() {
        controller.show(content, null) {}
        fake.removeFailure = IllegalArgumentException()
        controller.hide()
        assertFalse(controller.isShowing)
    }

    @Test
    fun hideThenShowUsesFreshView() {
        controller.show(content, null) {}
        controller.hide()
        assertEquals(ShowResult.SHOWN, controller.show(content, null) {})
        assertNotSame(fake.added[0].view, fake.added[1].view)
    }

    @Test
    fun keepScreenOnBeforeShowIsInParamsThenUpdates() {
        controller.setKeepScreenOn(true)
        controller.show(content, null) {}
        assertEquals(notFocusable or keepOn, fake.added[0].params.flags)
        controller.setKeepScreenOn(false)
        assertEquals(notFocusable, fake.updates.last().second.flags)
        val n = fake.updates.size
        controller.setKeepScreenOn(false)
        assertEquals(n, fake.updates.size)
    }

    @Test
    fun keepScreenOnWhileHiddenRemembersWithoutUpdate() {
        controller.setKeepScreenOn(true)
        assertTrue(fake.updates.isEmpty())
        controller.show(content, null) {}
        assertEquals(notFocusable or keepOn, fake.added[0].params.flags)
    }

    @Test
    fun initialPositionBeforeLayout() {
        controller.show(content, OverlayPosition(300, 50)) {}
        assertEquals(300, fake.added[0].params.x)
        assertEquals(50, fake.added[0].params.y)
    }

    @Test
    fun firstLayoutWithoutSavedPositionDocksRight() {
        controller.show(content, null) {}
        fake.added[0].view.layout(0, 0, 240, 300)
        val p = fake.updates.last().second
        assertEquals(1680, p.x)
        assertEquals(80, p.y)
    }

    @Test
    fun firstLayoutClampsSavedPosition() {
        controller.show(content, OverlayPosition(1900, 5000)) {}
        fake.added[0].view.layout(0, 0, 240, 300)
        val p = fake.updates.last().second
        assertEquals(1680, p.x)
        assertEquals(780, p.y)
    }

    @Test
    fun widthChangeReanchorsDockedRight() {
        controller.show(content, null) {}
        val v = fake.added[0].view
        v.layout(0, 0, 240, 300)
        v.layout(0, 0, 120, 300)
        assertEquals(1800, fake.updates.last().second.x)
    }

    @Test
    fun widthChangeKeepsMidScreenX() {
        controller.show(content, OverlayPosition(500, 100)) {}
        val v = fake.added[0].view
        v.layout(0, 0, 240, 300)
        v.layout(0, 0, 120, 300)
        assertEquals(500, fake.added[0].params.x)
    }

    @Test
    fun dragEndToEndSnapsAndReportsMoved() {
        val moved = mutableListOf<OverlayPosition>()
        controller.show(content, OverlayPosition(1000, 200)) { moved += it }
        val view = fake.added[0].view as DraggableFrameLayout
        val params = fake.added[0].params
        view.layout(0, 0, 240, 300)
        assertTrue(moved.isEmpty())
        view.onInterceptTouchEvent(ev(MotionEvent.ACTION_DOWN, 0f, 0f))
        assertTrue(view.onInterceptTouchEvent(ev(MotionEvent.ACTION_MOVE, slop + 1f, 0f)))
        assertEquals(1000 + slop + 1, params.x)
        view.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 650f, 0f))
        assertEquals(1650, params.x)
        view.onTouchEvent(ev(MotionEvent.ACTION_UP, 650f, 0f))
        assertEquals(1680, params.x)
        assertEquals(OverlayPosition(1680, 200), moved.last())

        view.onInterceptTouchEvent(ev(MotionEvent.ACTION_DOWN, 0f, 0f))
        assertTrue(view.onInterceptTouchEvent(ev(MotionEvent.ACTION_MOVE, 0f, -300f)))
        view.onTouchEvent(ev(MotionEvent.ACTION_UP, 0f, -300f))
        assertEquals(0, params.y)
        assertEquals(0, moved.last().y)
    }

    @Test
    fun tapNeverCallsOnMoved() {
        val moved = mutableListOf<OverlayPosition>()
        controller.show(content, null) { moved += it }
        val view = fake.added[0].view as DraggableFrameLayout
        view.layout(0, 0, 240, 300)
        view.onInterceptTouchEvent(ev(MotionEvent.ACTION_DOWN, 5f, 5f))
        view.onInterceptTouchEvent(ev(MotionEvent.ACTION_UP, 5f, 5f))
        assertTrue(moved.isEmpty())
    }
}

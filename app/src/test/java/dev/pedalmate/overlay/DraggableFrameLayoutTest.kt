package dev.pedalmate.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DraggableFrameLayoutTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val drags = mutableListOf<Pair<Int, Int>>()
    private var ends = 0
    private val layout = DraggableFrameLayout(ctx, { dx, dy -> drags += dx to dy }, { ends++ })

    private fun ev(action: Int, x: Float, y: Float) = MotionEvent.obtain(0L, 0L, action, x, y, 0)

    @Test
    fun tapIsNotIntercepted() {
        assertFalse(layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_DOWN, 100f, 100f)))
        assertFalse(layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_MOVE, 100f + slop / 2, 100f)))
        assertFalse(layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_UP, 100f + slop / 2, 100f)))
        assertTrue(drags.isEmpty())
        assertEquals(0, ends)
    }

    @Test
    fun dragIsInterceptedAfterSlopAndReportsDeltas() {
        assertFalse(layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_DOWN, 100f, 100f)))
        assertTrue(layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_MOVE, 100f + slop + 5, 100f)))
        assertEquals(listOf((slop + 5) to 0), drags)
        assertTrue(layout.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 100f + slop + 15, 130f)))
        assertEquals((slop + 5) to 0, drags[0])
        assertEquals(10 to 30, drags[1])
        assertTrue(layout.onTouchEvent(ev(MotionEvent.ACTION_UP, 100f + slop + 15, 130f)))
        assertEquals(1, ends)
    }

    @Test
    fun afterDragEndsNewTouchWithinSlopIsATap() {
        layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_DOWN, 100f, 100f))
        layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_MOVE, 100f + slop + 5, 100f))
        layout.onTouchEvent(ev(MotionEvent.ACTION_UP, 100f + slop + 5, 100f))
        assertFalse(layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_DOWN, 300f, 300f)))
        assertFalse(layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_MOVE, 300f + slop / 2, 300f)))
        assertEquals(1, ends)
    }

    @Test
    fun cancelDuringDragCallsDragEndOnce() {
        layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_DOWN, 100f, 100f))
        layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_MOVE, 100f + slop + 5, 100f))
        assertTrue(layout.onTouchEvent(ev(MotionEvent.ACTION_CANCEL, 100f + slop + 5, 100f)))
        assertEquals(1, ends)
    }

    @Test
    fun dragStartsInOnTouchEventWhenNoChildConsumedDown() {
        assertTrue(layout.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 100f, 100f)))
        assertTrue(layout.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 100f + slop + 5, 100f)))
        assertEquals(listOf((slop + 5) to 0), drags)
        assertTrue(layout.onTouchEvent(ev(MotionEvent.ACTION_UP, 100f + slop + 5, 100f)))
        assertEquals(1, ends)
    }

    @Test
    fun tapHandledInOnTouchEventNeverDrags() {
        assertTrue(layout.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 100f, 100f)))
        layout.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 100f + slop / 2, 100f))
        layout.onTouchEvent(ev(MotionEvent.ACTION_UP, 100f + slop / 2, 100f))
        assertTrue(drags.isEmpty())
        assertEquals(0, ends)
    }

    @Test
    fun fractionalMovesDoNotDrift() {
        layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_DOWN, 100f, 100f))
        val startX = 100f + slop + 5
        layout.onInterceptTouchEvent(ev(MotionEvent.ACTION_MOVE, startX, 100f))
        var x = startX
        repeat(20) {
            x += 0.6f
            layout.onTouchEvent(ev(MotionEvent.ACTION_MOVE, x, 100f))
        }
        val totalDx = drags.sumOf { it.first }
        // Truncation must not lose the sub-pixel remainder: total emitted equals the whole-pixel distance travelled.
        assertEquals((x - 100f).toInt(), totalDx)
    }
}

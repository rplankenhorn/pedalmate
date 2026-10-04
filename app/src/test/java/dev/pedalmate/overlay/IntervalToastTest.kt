package dev.pedalmate.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntervalToastTest {
    private class FakeToastWindow : ToastWindow {
        val calls = mutableListOf<String>()
        var visibleTitle: String? = null
        override fun show(model: ToastModel) { calls += "show:${model.title}"; visibleTitle = model.title }
        override fun hide() { calls += "hide"; visibleTitle = null }
    }

    private fun model(t: String) = ToastModel("NEXT INTERVAL", t, null, "1:00")

    @Test fun visibleThenHidden() = runTest {
        val w = FakeToastWindow()
        val toast = IntervalToast(backgroundScope, w)
        toast.show(model("A")); runCurrent()
        advanceTimeBy(3_999); runCurrent()
        assertEquals("A", w.visibleTitle)
        advanceTimeBy(1); runCurrent()
        assertNull(w.visibleTitle)
        assertEquals(listOf("show:A", "hide"), w.calls)
    }

    @Test fun replacementRestartsTimer() = runTest {
        val w = FakeToastWindow()
        val toast = IntervalToast(backgroundScope, w)
        toast.show(model("A")); runCurrent()
        advanceTimeBy(2_000)
        toast.show(model("B"))
        advanceTimeBy(3_999); runCurrent()
        assertEquals("B", w.visibleTitle)
        advanceTimeBy(1); runCurrent()
        assertNull(w.visibleTitle)
        assertEquals(listOf("show:A", "show:B", "hide"), w.calls)
    }

    @Test fun burstLeavesOnlyLast() = runTest {
        val w = FakeToastWindow()
        val toast = IntervalToast(backgroundScope, w)
        toast.show(model("A")); toast.show(model("B")); toast.show(model("C")); runCurrent()
        assertEquals(listOf("show:A", "show:B", "show:C"), w.calls)
        assertEquals("C", w.visibleTitle)
        advanceTimeBy(4_000); runCurrent()
        assertEquals(1, w.calls.count { it == "hide" })
    }

    @Test fun cancelHidesAtOnceAndStopsTimer() = runTest {
        val w = FakeToastWindow()
        val toast = IntervalToast(backgroundScope, w)
        toast.show(model("A")); runCurrent()
        toast.cancel()
        assertEquals("hide", w.calls.last())
        advanceTimeBy(10_000_000); runCurrent()
        assertEquals(1, w.calls.count { it == "hide" })
    }

    @Test fun cancelWhenNothingShownHidesOnce() = runTest {
        val w = FakeToastWindow()
        IntervalToast(backgroundScope, w).cancel()
        assertEquals(listOf("hide"), w.calls)
    }

    @Test fun newToastAfterExpiryGetsFreshTimer() = runTest {
        val w = FakeToastWindow()
        val toast = IntervalToast(backgroundScope, w)
        toast.show(model("A")); runCurrent()
        advanceTimeBy(4_000); runCurrent()
        toast.show(model("B"))
        advanceTimeBy(3_999); runCurrent()
        assertEquals("B", w.visibleTitle)
        advanceTimeBy(1); runCurrent()
        assertNull(w.visibleTitle)
    }

    @Test fun customDismissAfterIsHonoured() = runTest {
        val w = FakeToastWindow()
        val toast = IntervalToast(backgroundScope, w, dismissAfterMs = 1_000L)
        toast.show(model("A")); runCurrent()
        advanceTimeBy(999); runCurrent()
        assertEquals("A", w.visibleTitle)
        advanceTimeBy(1); runCurrent()
        assertNull(w.visibleTitle)
    }
}

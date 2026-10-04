package dev.pedalmate.overlay

import dev.pedalmate.ride.RideStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPolicyTest {
    @Test fun directives() {
        assertEquals(OverlayDirective(false, false), OverlayPolicy.directive(RideStatus.IDLE))
        assertEquals(OverlayDirective(true, true), OverlayPolicy.directive(RideStatus.RUNNING))
        assertEquals(OverlayDirective(true, false), OverlayPolicy.directive(RideStatus.PAUSED))
        assertEquals(OverlayDirective(true, false), OverlayPolicy.directive(RideStatus.FINISHED))
    }

    @Test fun `refresh shows only a visible directive that is not already showing`() {
        assertEquals(true, OverlayPolicy.needsShow(RideStatus.RUNNING, isShowing = false))
        assertEquals(true, OverlayPolicy.needsShow(RideStatus.PAUSED, isShowing = false))
        assertEquals(false, OverlayPolicy.needsShow(RideStatus.RUNNING, isShowing = true))
        assertEquals(false, OverlayPolicy.needsShow(RideStatus.IDLE, isShowing = false))
    }
}

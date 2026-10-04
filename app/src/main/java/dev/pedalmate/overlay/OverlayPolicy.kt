package dev.pedalmate.overlay

import dev.pedalmate.ride.RideStatus

/** Whether the overlay should be on screen and whether the screen should be kept awake. */
data class OverlayDirective(val visible: Boolean, val keepScreenOn: Boolean)

/** Maps ride status to an [OverlayDirective]. */
object OverlayPolicy {
    fun directive(status: RideStatus) = OverlayDirective(
        visible = status != RideStatus.IDLE,
        keepScreenOn = status == RideStatus.RUNNING,
    )

    /** True when a refresh should call show: the ride wants the panel but it is not on screen. */
    fun needsShow(status: RideStatus, isShowing: Boolean) = directive(status).visible && !isShowing
}

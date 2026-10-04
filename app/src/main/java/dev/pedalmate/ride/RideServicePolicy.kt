package dev.pedalmate.ride

/** What [RideService.onStartCommand] should do after entering the foreground. */
enum class ServiceAction { START, STOP, CONTINUE, STOP_SELF }

object RideServicePolicy {
    /**
     * A null intent (START_STICKY restart) or unknown action continues an active session and
     * stops itself otherwise, so a restart never shows a phantom ride.
     */
    fun decide(action: String?, intentIsNull: Boolean, sessionActive: Boolean): ServiceAction = when {
        action == RideService.ACTION_START -> ServiceAction.START
        action == RideService.ACTION_STOP -> ServiceAction.STOP
        sessionActive -> ServiceAction.CONTINUE
        else -> ServiceAction.STOP_SELF
    }
}

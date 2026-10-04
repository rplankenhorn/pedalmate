package dev.pedalmate.ui.setup

import android.content.Context
import dev.pedalmate.ride.RideService
import dev.pedalmate.ride.RideSession

/** Ride controls the setup screen can issue. */
interface RideCommands {
    fun start(workoutId: String?)
    fun stop()
    fun pause()
    fun resume()
    fun skip()
}

/** [RideCommands] backed by [RideService] (start/stop) and [RideSession] (pause/resume/skip). */
class ServiceRideCommands(private val context: Context, private val session: RideSession) : RideCommands {
    override fun start(workoutId: String?) = RideService.start(context, workoutId)
    override fun stop() { context.startService(RideService.stopIntent(context)) }
    override fun pause() = session.pause()
    override fun resume() = session.resume()
    override fun skip() = session.skip()
}

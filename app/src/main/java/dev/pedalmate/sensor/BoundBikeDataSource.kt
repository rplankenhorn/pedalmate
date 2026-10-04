// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/sensor/BoundBikeDataSource.kt. Modified by PedalMate.
package dev.pedalmate.sensor

/**
 * A [BikeDataSource] backed by a binder connection that must be explicitly opened and closed.
 *
 * Lets consumers (such as the bike source supervisor) drive and test a source without depending
 * on the concrete AIDL-bound classes, so they stay unit-testable on a plain JVM.
 */
interface BoundBikeDataSource : BikeDataSource {
    /** Opens the binding. Must never throw, on any device. */
    fun start()

    /** Closes the binding. Must be safe to call even if [start] never ran. */
    fun stop()
}

/**
 * A [BoundBikeDataSource] that can also be read synchronously, rather than only pushed to.
 *
 * Only the `IBikeInterface` path offers this (`getBikeData`, transaction 14). It matters because
 * "the bind succeeded, the callback registered, and nothing was ever pushed" is real observed
 * hardware behaviour, and a synchronous read still works in that state.
 */
interface PollableBikeDataSource : BoundBikeDataSource {
    /**
     * Count of frames decoded since the source was created (pushed or polled). Consumers detect
     * liveness from this counter, NOT from [metrics] emissions: a StateFlow drops consecutive
     * equal values, and a coasting rider legitimately sends identical frames.
     */
    val framesReceived: Long

    /**
     * Reads one frame synchronously and publishes it as if it had been pushed. Returns the
     * decoded metrics, or `null` if not bound or the read failed.
     */
    fun pollBikeData(): BikeMetrics?
}

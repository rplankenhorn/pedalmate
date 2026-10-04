// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/sensor/BikeDataSource.kt. Modified by PedalMate.
package dev.pedalmate.sensor

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the bike's live sensor feed (cadence/resistance/power).
 *
 * Kept as a narrow interface so the real implementation (binding the Bike+ affernet service)
 * and `MockBikeDataSource` (used for development in a standard emulator) are interchangeable
 * everywhere else in the app.
 */
interface BikeDataSource {
    /** Latest sensor reading. Consumers sample this at whatever cadence they need. */
    val metrics: StateFlow<BikeMetrics>

    /** Whether the sensor feed is currently reachable. */
    val connectionState: StateFlow<ConnectionState>
}

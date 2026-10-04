package dev.pedalmate.ride

import dev.pedalmate.heartrate.ManagedHeartRateDataSource
import dev.pedalmate.sensor.BoundBikeDataSource

/** Reference-counted owner of the bike and heart-rate sensors. */
class SensorHub(val bike: BoundBikeDataSource, val hr: ManagedHeartRateDataSource) {
    private var holders = 0

    /** First holder starts both sensors; the Activity debug view and a ride can share them. */
    @Synchronized fun acquire() { if (holders++ == 0) { bike.start(); hr.start() } }

    /** Last holder stops both sensors; extra releases are ignored. */
    @Synchronized fun release() { if (holders > 0 && --holders == 0) { bike.stop(); hr.stop() } }
}

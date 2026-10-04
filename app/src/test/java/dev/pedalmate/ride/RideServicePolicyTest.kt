package dev.pedalmate.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class RideServicePolicyTest {
    private fun check(action: String?, isNull: Boolean, active: Boolean, expected: ServiceAction) =
        assertEquals("$action null=$isNull active=$active", expected, RideServicePolicy.decide(action, isNull, active))

    @Test fun `decision table`() {
        check(RideService.ACTION_START, false, false, ServiceAction.START)
        check(RideService.ACTION_START, false, true, ServiceAction.START)
        check(RideService.ACTION_STOP, false, true, ServiceAction.STOP)
        check(null, true, true, ServiceAction.CONTINUE)
        check(null, true, false, ServiceAction.STOP_SELF)
        check(null, false, false, ServiceAction.STOP_SELF)
        check("some.other.action", false, true, ServiceAction.CONTINUE)
        check("some.other.action", false, false, ServiceAction.STOP_SELF)
    }

    @Test fun `refresh overlay is explicit and never keeps an idle service alive`() {
        check(RideService.ACTION_REFRESH_OVERLAY, false, true, ServiceAction.REFRESH_OVERLAY)
        check(RideService.ACTION_REFRESH_OVERLAY, false, false, ServiceAction.STOP_SELF)
    }
}

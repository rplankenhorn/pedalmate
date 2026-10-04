package dev.pedalmate.ui.setup

import dev.pedalmate.sensor.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class HrStatusTextTest {
    @Test
    fun `nothing saved and nothing reporting`() {
        assertEquals("HR: not paired", hrStatusText(HrCardState(null, ConnectionState.Unavailable, null)))
        assertEquals("HR: not paired", hrStatusText(HrCardState(null, ConnectionState.Disconnected, null)))
    }

    @Test
    fun `mock flavor reports a connection with no saved device`() {
        val t = hrStatusText(HrCardState(null, ConnectionState.Connected, 113))
        assertEquals("HR: connected, 113 bpm", t)
    }

    @Test
    fun `saved device connected`() {
        assertEquals("HR: HR-1 (AA:BB), connected, 72 bpm", hrStatusText(HrCardState("HR-1 (AA:BB)", ConnectionState.Connected, 72)))
    }

    @Test
    fun `saved device not connected`() {
        assertEquals("HR: HR-1 (AA:BB), not connected", hrStatusText(HrCardState("HR-1 (AA:BB)", ConnectionState.Disconnected, null)))
        assertEquals("HR: HR-1 (AA:BB), not connected", hrStatusText(HrCardState("HR-1 (AA:BB)", ConnectionState.Unavailable, 70)))
    }

    @Test
    fun `connected without a bpm yet`() {
        assertEquals("HR: connected", hrStatusText(HrCardState(null, ConnectionState.Connected, null)))
    }
}

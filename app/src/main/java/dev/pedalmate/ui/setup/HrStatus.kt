package dev.pedalmate.ui.setup

import dev.pedalmate.sensor.ConnectionState

/**
 * One consistent status line: "not paired" only when nothing is saved and nothing is connected;
 * a connected source without a saved device (mock flavor) shows just the connection and bpm.
 */
fun hrStatusText(hr: HrCardState): String {
    val connected = hr.connection == ConnectionState.Connected
    val parts = mutableListOf<String>()
    if (hr.deviceLabel != null) parts += hr.deviceLabel
    if (connected) {
        parts += "connected"
        hr.bpm?.let { parts += "$it bpm" }
    } else if (hr.deviceLabel != null) {
        parts += "not connected"
    } else {
        return "HR: not paired"
    }
    return "HR: " + parts.joinToString(", ")
}

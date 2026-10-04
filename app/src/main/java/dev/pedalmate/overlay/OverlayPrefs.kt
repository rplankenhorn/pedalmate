package dev.pedalmate.overlay

import dev.pedalmate.data.SettingsStore
import kotlinx.coroutines.flow.first

/** Persisted overlay state; [position] is null unless both coordinates were stored. */
data class OverlayPrefsState(val minimized: Boolean, val position: OverlayPosition?)

/** Persists overlay panel mode and position through [SettingsStore]. */
class OverlayPrefs(private val settings: SettingsStore) {
    suspend fun load(): OverlayPrefsState {
        val s = settings.settings.first()
        val x = s.overlayX
        val y = s.overlayY
        return OverlayPrefsState(s.overlayMinimized, if (x != null && y != null) OverlayPosition(x, y) else null)
    }

    suspend fun setMinimized(m: Boolean) = settings.setOverlayMinimized(m)

    suspend fun setPosition(p: OverlayPosition) = settings.setOverlayPosition(p.x, p.y)
}

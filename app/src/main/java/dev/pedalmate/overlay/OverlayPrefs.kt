package dev.pedalmate.overlay

import dev.pedalmate.data.SettingsStore
import kotlinx.coroutines.flow.first

/** Persisted overlay state; [placement] is null unless both coordinates were stored (size defaults to 0 for pre-R3 prefs). */
data class OverlayPrefsState(val placement: OverlayPlacement?)

/** Persists overlay panel position through [SettingsStore]. */
class OverlayPrefs(private val settings: SettingsStore) {
    suspend fun load(): OverlayPrefsState {
        val s = settings.settings.first()
        val x = s.overlayX
        val y = s.overlayY
        return OverlayPrefsState(if (x != null && y != null) OverlayPlacement(OverlayPosition(x, y), s.overlayW ?: 0, s.overlayH ?: 0) else null)
    }

    suspend fun setPlacement(p: OverlayPlacement) = settings.setOverlayPosition(p.position.x, p.position.y, p.viewW, p.viewH)
}

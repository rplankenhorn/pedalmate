package dev.pedalmate.overlay

import androidx.compose.ui.unit.Density

/** Uniform shrink applied to the whole overlay (dp and sp alike) so the panel clears the Lichess game-over dialog on the bike. */
const val OVERLAY_SCALE = 0.58f

/** The overlay renders at [OVERLAY_SCALE] of the host density; fontScale is kept so accessibility text scaling still applies. */
fun Density.scaledForOverlay(): Density = Density(density * OVERLAY_SCALE, fontScale)

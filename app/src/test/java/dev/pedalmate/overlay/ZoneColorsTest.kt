package dev.pedalmate.overlay

import androidx.compose.ui.graphics.Color
import dev.pedalmate.workout.PowerZone
import dev.pedalmate.workout.TargetStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneColorsTest {
    @Test fun everyZoneMapsToItsPelotonColor() {
        val expected = listOf(0xFF8183F9, 0xFF31BCFD, 0xFF0BDAA6, 0xFFA8D90C, 0xFFFDC619, 0xFFFE8935, 0xFFEB4756)
        PowerZone.values().forEachIndexed { i, z -> assertEquals(Color(expected[i]), ZoneColors.of(z)) }
    }

    @Test fun statusColorsMatchPalette() {
        assertEquals(Color(0xFF31BCFD), ZoneColors.status(TargetStatus.BELOW))
        assertEquals(Color(0xFF0BDAA6), ZoneColors.status(TargetStatus.IN))
        assertEquals(Color(0xFFEB4756), ZoneColors.status(TargetStatus.ABOVE))
        assertEquals(Color.White, ZoneColors.status(null))
    }

    @Test fun unknownZoneIsGrey() {
        assertEquals(Color(0xFF616161), ZoneColors.of(null))
    }
}

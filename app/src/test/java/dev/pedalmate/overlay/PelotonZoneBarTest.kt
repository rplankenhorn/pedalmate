package dev.pedalmate.overlay

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PelotonZoneBarTest {
    @Test fun firstSegmentIsRoundedOnTheLeftOnly() {
        assertEquals(RoundedCornerShape(topStart = 6.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 6.dp), segmentShape(0))
    }

    @Test fun lastSegmentIsRoundedOnTheRightOnly() {
        assertEquals(RoundedCornerShape(topStart = 0.dp, topEnd = 6.dp, bottomEnd = 6.dp, bottomStart = 0.dp), segmentShape(6))
    }

    @Test fun middleSegmentsAreSquare() {
        for (i in 1..5) assertEquals(RoundedCornerShape(0.dp), segmentShape(i))
    }
}

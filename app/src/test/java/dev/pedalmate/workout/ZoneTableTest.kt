package dev.pedalmate.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneTableTest {
    private fun rows(ftp: Int): List<Pair<Int, Int?>> =
        ZoneTable.forFtp(ftp)!!.ranges.map { it.lowWatts to it.highWatts }

    @Test fun `FTP 200 matches the spec table exactly`() {
        assertEquals(
            listOf(0 to 109, 110 to 149, 150 to 179, 180 to 209, 210 to 239, 240 to 299, 300 to null),
            rows(200),
        )
    }

    @Test fun `FTP 248`() = assertEquals(
        listOf(0 to 136, 137 to 185, 186 to 223, 224 to 260, 261 to 297, 298 to 371, 372 to null), rows(248))

    @Test fun `FTP 233`() = assertEquals(
        listOf(0 to 128, 129 to 174, 175 to 209, 210 to 244, 245 to 279, 280 to 349, 350 to null), rows(233))

    @Test fun `FTP 150 rounds 82_5 up`() = assertEquals(
        listOf(0 to 82, 83 to 112, 113 to 134, 135 to 157, 158 to 179, 180 to 224, 225 to null), rows(150))

    @Test fun `FTP 600`() = assertEquals(
        listOf(0 to 329, 330 to 449, 450 to 539, 540 to 629, 630 to 719, 720 to 899, 900 to null), rows(600))

    @Test fun `FTP 1 has empty ranges instead of failing`() {
        assertEquals(
            listOf(0 to 0, 1 to 0, 1 to 0, 1 to 1, 2 to 1, 2 to 1, 2 to null), rows(1))
        val t = ZoneTable.forFtp(1)!!
        assertTrue(t.rangeOf(PowerZone.Z2).isEmpty)
        assertEquals(PowerZone.Z4, t.zoneFor(1))
    }

    @Test fun `zone boundaries at FTP 200`() {
        val t = ZoneTable.forFtp(200)!!
        assertEquals(PowerZone.Z1, t.zoneFor(109)); assertEquals(PowerZone.Z2, t.zoneFor(110))
        assertEquals(PowerZone.Z6, t.zoneFor(299)); assertEquals(PowerZone.Z7, t.zoneFor(300))
        assertEquals(PowerZone.Z1, t.zoneFor(0));   assertEquals(PowerZone.Z1, t.zoneFor(-40))
        assertEquals(PowerZone.Z7, t.zoneFor(5_000))
    }

    @Test fun `null zero and negative FTP give no table`() {
        assertNull(ZoneTable.forFtp(null)); assertNull(ZoneTable.forFtp(0)); assertNull(ZoneTable.forFtp(-5))
        assertNull(ZoneTable.forFtp(2_000_000))
    }

    @Test fun `every watt value belongs to exactly one row and zoneFor agrees`() {
        for (ftp in listOf(1, 150, 200, 233, 248, 600)) {
            val t = ZoneTable.forFtp(ftp)!!
            for (p in 0..3 * ftp) {
                val containing = t.ranges.filter { p >= it.lowWatts && (it.highWatts == null || p <= it.highWatts!!) }
                assertEquals("ftp=$ftp p=$p rows=$containing", 1, containing.size)
                assertEquals("ftp=$ftp p=$p", containing.single().zone, t.zoneFor(p))
            }
        }
    }

    @Test fun `fromNumber maps 1 to 7 and rejects others`() {
        assertEquals(PowerZone.Z1, PowerZone.fromNumber(1)); assertEquals(PowerZone.Z7, PowerZone.fromNumber(7))
        assertNull(PowerZone.fromNumber(0)); assertNull(PowerZone.fromNumber(8))
    }
}

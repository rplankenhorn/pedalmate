package dev.pedalmate.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LichessLauncherTest {
    @Test
    fun `configured installed wins`() {
        assertEquals("org.lichess.mobileV2", LichessLauncher.resolve("org.lichess.mobileV2") { it == "org.lichess.mobileV2" })
    }

    @Test
    fun `fallback when configured missing`() {
        assertEquals("org.lichess.mobileapp", LichessLauncher.resolve("org.lichess.mobileV2") { it == "org.lichess.mobileapp" })
    }

    @Test
    fun `neither installed gives null`() {
        assertNull(LichessLauncher.resolve("org.lichess.mobileV2") { false })
    }

    @Test
    fun `configured equal to fallback and missing checks once`() {
        var calls = 0
        assertNull(LichessLauncher.resolve(LichessLauncher.FALLBACK_PACKAGE) { calls++; false })
        assertEquals(1, calls)
    }

    @Test
    fun `custom installed beats installed fallback`() {
        assertEquals("org.x", LichessLauncher.resolve("org.x") { true })
    }
}

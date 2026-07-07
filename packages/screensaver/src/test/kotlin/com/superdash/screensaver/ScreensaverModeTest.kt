package com.superdash.screensaver

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreensaverModeTest {
    @Test
    fun `fromKey black returns Black`() {
        assertEquals(ScreensaverMode.Black, ScreensaverMode.fromKey("black"))
    }

    @Test
    fun `Black key is the string black`() {
        assertEquals("black", ScreensaverMode.Black.key)
    }

    @Test
    fun `unknown key still falls back to Photos`() {
        assertEquals(ScreensaverMode.Photos, ScreensaverMode.fromKey("nonsense"))
    }
}

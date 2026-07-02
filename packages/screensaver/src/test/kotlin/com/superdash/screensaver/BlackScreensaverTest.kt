package com.superdash.screensaver

import org.junit.Assert.assertEquals
import org.junit.Test

class BlackScreensaverTest {
    @Test
    fun `applyBrightnessOverride saves prior value and writes zero`() {
        var current = 0.42f
        val saved =
            BlackScreensaver.applyBrightnessOverride(
                readBrightness = { current },
                writeBrightness = { value -> current = value },
            )
        assertEquals(0.42f, saved, 0.0001f)
        assertEquals(0f, current, 0.0001f)
    }

    @Test
    fun `restoreBrightness writes the saved value back`() {
        var current = 0f
        BlackScreensaver.restoreBrightness(
            saved = 0.42f,
            writeBrightness = { value -> current = value },
        )
        assertEquals(0.42f, current, 0.0001f)
    }

    @Test
    fun `restoreBrightness with BRIGHTNESS_OVERRIDE_NONE writes that value`() {
        var current = 0f
        BlackScreensaver.restoreBrightness(
            saved = BlackScreensaver.BRIGHTNESS_OVERRIDE_NONE,
            writeBrightness = { value -> current = value },
        )
        assertEquals(BlackScreensaver.BRIGHTNESS_OVERRIDE_NONE, current, 0.0001f)
    }
}

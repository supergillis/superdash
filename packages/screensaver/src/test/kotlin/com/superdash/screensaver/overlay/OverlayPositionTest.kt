package com.superdash.screensaver.overlay

import com.superdash.screensaver.overlay.OverlayPosition.BottomLeft
import com.superdash.screensaver.overlay.OverlayPosition.BottomRight
import com.superdash.screensaver.overlay.OverlayPosition.Random
import com.superdash.screensaver.overlay.OverlayPosition.TopLeft
import com.superdash.screensaver.overlay.OverlayPosition.TopRight
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionTest {
    @Test
    fun `opposite round-trips for fixed corners`() {
        assertEquals(BottomRight, TopLeft.opposite())
        assertEquals(BottomLeft, TopRight.opposite())
        assertEquals(TopRight, BottomLeft.opposite())
        assertEquals(TopLeft, BottomRight.opposite())
    }

    @Test
    fun `Random opposite is Random`() {
        assertEquals(Random, Random.opposite())
    }

    @Test
    fun `fromKey resolves known keys`() {
        assertEquals(TopLeft, OverlayPosition.fromKey("top_left"))
        assertEquals(Random, OverlayPosition.fromKey("random"))
    }

    @Test
    fun `fromKey falls back to BottomLeft on unknown`() {
        assertEquals(BottomLeft, OverlayPosition.fromKey("nonsense"))
        assertEquals(BottomLeft, OverlayPosition.fromKey(""))
    }
}

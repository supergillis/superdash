package com.superdash.screensaver.overlay

import com.superdash.screensaver.overlay.OverlayPosition.BottomLeft
import com.superdash.screensaver.overlay.OverlayPosition.BottomRight
import com.superdash.screensaver.overlay.OverlayPosition.Random
import com.superdash.screensaver.overlay.OverlayPosition.TopLeft
import com.superdash.screensaver.overlay.OverlayPosition.TopRight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CornerAssignmentTest {
    @Test
    fun `freeCornersFor BottomLeft starts with diagonal TopRight`() {
        assertEquals(listOf(TopRight, TopLeft, BottomRight), freeCornersFor(BottomLeft))
    }

    @Test
    fun `freeCornersFor BottomRight starts with diagonal TopLeft`() {
        assertEquals(listOf(TopLeft, TopRight, BottomLeft), freeCornersFor(BottomRight))
    }

    @Test
    fun `freeCornersFor TopLeft starts with diagonal BottomRight`() {
        assertEquals(listOf(BottomRight, BottomLeft, TopRight), freeCornersFor(TopLeft))
    }

    @Test
    fun `freeCornersFor TopRight starts with diagonal BottomLeft`() {
        assertEquals(listOf(BottomLeft, BottomRight, TopLeft), freeCornersFor(TopRight))
    }

    @Test
    fun `freeCornersFor Random throws`() {
        try {
            freeCornersFor(Random)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Random"))
        }
    }

    @Test
    fun `pickRandomCorner returns one of the four fixed corners`() {
        val seen = mutableSetOf<OverlayPosition>()
        repeat(50) { seen.add(pickRandomCorner()) }
        assertEquals(4, seen.size)
        assertTrue(seen.none { it == Random })
    }
}

package com.superdash.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class Nv21RotatorTest {
    // 4x2 NV21: Y = row-major 0..7, chroma = one VU pair per 2x2 block.
    private val nv21 =
        byteArrayOf(
            0,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            100,
            101,
            102,
            103, // V0 U0 V1 U1
        )

    @Test
    fun `rotate 0 returns the same buffer`() {
        val rotated = Nv21Rotator.rotate(nv21, width = 4, height = 2, degrees = 0)
        assertSame(nv21, rotated.nv21)
        assertEquals(4, rotated.width)
        assertEquals(2, rotated.height)
    }

    @Test
    fun `rotate 90 swaps dimensions and remaps luma clockwise`() {
        val rotated = Nv21Rotator.rotate(nv21, width = 4, height = 2, degrees = 90)
        assertEquals(2, rotated.width)
        assertEquals(4, rotated.height)
        // Clockwise: new(x, y) = old(y', x') with newY0 row = old first column bottom-up.
        assertArrayEquals(
            byteArrayOf(4, 0, 5, 1, 6, 2, 7, 3),
            rotated.nv21.copyOfRange(0, 8),
        )
    }

    @Test
    fun `rotate 180 reverses luma`() {
        val rotated = Nv21Rotator.rotate(nv21, width = 4, height = 2, degrees = 180)
        assertEquals(4, rotated.width)
        assertEquals(2, rotated.height)
        assertArrayEquals(
            byteArrayOf(7, 6, 5, 4, 3, 2, 1, 0),
            rotated.nv21.copyOfRange(0, 8),
        )
    }
}

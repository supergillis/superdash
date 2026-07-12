package com.superdash.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRateGateTest {
    @Test
    fun `admits the first frame`() {
        val gate = FrameRateGate(maxFps = 10)
        assertTrue(gate.admit(nowMs = 0L))
    }

    @Test
    fun `drops frames arriving faster than the cap and admits at the interval`() {
        val gate = FrameRateGate(maxFps = 10)
        assertTrue(gate.admit(0L))
        assertFalse(gate.admit(33L))
        assertFalse(gate.admit(66L))
        assertFalse(gate.admit(99L))
        assertTrue(gate.admit(100L))
    }

    @Test
    fun `interval derives from maxFps`() {
        val gate = FrameRateGate(maxFps = 2)
        assertTrue(gate.admit(0L))
        assertFalse(gate.admit(499L))
        assertTrue(gate.admit(500L))
    }

    @Test
    fun `schedules the next admission from the previous due time`() {
        val gate = FrameRateGate(maxFps = 10)
        assertTrue(gate.admit(0L))
        assertFalse(gate.admit(99L))
        assertTrue(gate.admit(150L))
        assertFalse(gate.admit(199L))
        assertTrue(gate.admit(200L))
    }

    @Test
    fun `catches up when arrivals are coarser than the interval`() {
        // 15 fps arrivals under a 10 fps cap: 10 admits per second, not 7.5.
        val gate = FrameRateGate(maxFps = 10)
        val arrivals = (0..14).map { index -> index * 1000L / 15 }
        assertEquals(10, arrivals.count { arrival -> gate.admit(arrival) })
    }

    @Test
    fun `resets the schedule after a long gap`() {
        val gate = FrameRateGate(maxFps = 10)
        assertTrue(gate.admit(0L))
        assertTrue(gate.admit(5_000L))
        assertFalse(gate.admit(5_099L))
        assertTrue(gate.admit(5_100L))
    }

    @Test
    fun `maxFps 30 admits a steady 30 fps arrival stream`() {
        val gate = FrameRateGate(maxFps = 30)
        val arrivals = (0..29).map { index -> index * 1000L / 30 }
        assertEquals(30, arrivals.count { arrival -> gate.admit(arrival) })
    }

    @Test
    fun `maxFps 1 admits one frame per second`() {
        val gate = FrameRateGate(maxFps = 1)
        val arrivals = (0 until 90).map { index -> index * 100L }
        assertEquals(9, arrivals.count { arrival -> gate.admit(arrival) })
    }
}

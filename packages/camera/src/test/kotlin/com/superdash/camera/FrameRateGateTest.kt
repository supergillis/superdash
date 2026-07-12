package com.superdash.camera

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
    fun `spacing is measured from the last admitted frame`() {
        val gate = FrameRateGate(maxFps = 10)
        assertTrue(gate.admit(0L))
        assertFalse(gate.admit(99L))
        assertTrue(gate.admit(150L))
        assertFalse(gate.admit(240L))
        assertTrue(gate.admit(250L))
    }
}

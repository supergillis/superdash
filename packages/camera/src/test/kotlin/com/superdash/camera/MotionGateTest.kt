package com.superdash.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionGateTest {
    @Test
    fun `inactive until first detection`() {
        val gate = MotionGate(clearDelayMs = { 10_000L })
        assertFalse(gate.update(detected = false, nowMs = 0L))
    }

    @Test
    fun `detection activates and holds for the clear delay`() {
        val gate = MotionGate(clearDelayMs = { 10_000L })
        assertTrue(gate.update(detected = true, nowMs = 0L))
        assertTrue(gate.update(detected = false, nowMs = 9_999L))
        assertFalse(gate.update(detected = false, nowMs = 10_000L))
    }

    @Test
    fun `new detection extends the hold`() {
        val gate = MotionGate(clearDelayMs = { 10_000L })
        gate.update(detected = true, nowMs = 0L)
        gate.update(detected = true, nowMs = 8_000L)
        assertTrue(gate.update(detected = false, nowMs = 17_999L))
        assertFalse(gate.update(detected = false, nowMs = 18_000L))
    }

    @Test
    fun `zero clear delay is active only on detection frames`() {
        val gate = MotionGate(clearDelayMs = { 0L })
        assertTrue(gate.update(detected = true, nowMs = 0L))
        assertFalse(gate.update(detected = false, nowMs = 0L))
        assertFalse(gate.update(detected = false, nowMs = 1L))
    }

    @Test
    fun `clear delay is read at detection time`() {
        var delay = 10_000L
        val gate = MotionGate(clearDelayMs = { delay })
        gate.update(detected = true, nowMs = 0L)
        delay = 1_000L
        // Existing hold keeps the old deadline; the new delay applies on next detection.
        assertTrue(gate.update(detected = false, nowMs = 5_000L))
        gate.update(detected = true, nowMs = 20_000L)
        assertFalse(gate.update(detected = false, nowMs = 21_000L))
    }
}

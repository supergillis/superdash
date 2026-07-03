package com.superdash.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceServiceStartPolicyTest {
    @Test
    fun `does not request start when mic permission is missing`() {
        assertFalse(
            VoiceServiceStartPolicy.shouldRequestStart(
                shouldRun = true,
                hasMicPermission = false,
            ),
        )
    }

    @Test
    fun `does not request start when voice should not run`() {
        assertFalse(
            VoiceServiceStartPolicy.shouldRequestStart(
                shouldRun = false,
                hasMicPermission = true,
            ),
        )
    }

    @Test
    fun `requests start when voice should run and mic permission is granted`() {
        assertTrue(
            VoiceServiceStartPolicy.shouldRequestStart(
                shouldRun = true,
                hasMicPermission = true,
            ),
        )
    }

    @Test
    fun `foreground start denial is not treated as started`() {
        val started =
            VoiceServiceStartPolicy.tryStartForeground {
                throw SecurityException("microphone foreground service denied")
            }

        assertFalse(started)
    }

    @Test
    fun `background foreground start rejection is not treated as started`() {
        val started =
            VoiceServiceStartPolicy.tryStartForeground {
                // ForegroundServiceStartNotAllowedException is an IllegalStateException.
                throw IllegalStateException("startForeground not allowed from background")
            }

        assertFalse(started)
    }

    @Test
    fun `stops when voice should not run`() {
        assertTrue(VoiceServiceStartPolicy.shouldStopForShouldRun(shouldRun = false))
    }

    @Test
    fun `does not stop when voice should run`() {
        assertFalse(VoiceServiceStartPolicy.shouldStopForShouldRun(shouldRun = true))
    }

    @Test
    fun `skip reason names disabled voice before permission`() {
        assertFalse(
            VoiceServiceStartPolicy.shouldRequestStart(
                shouldRun = false,
                hasMicPermission = false,
            ),
        )
        assertEquals(
            "voice_disabled",
            VoiceServiceStartPolicy.skipStartReason(
                shouldRun = false,
                hasMicPermission = false,
            ),
        )
    }

    @Test
    fun `foreground start success is treated as started`() {
        val started = VoiceServiceStartPolicy.tryStartForeground {}

        assertTrue(started)
    }
}

package com.superdash.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceStartPolicyTest {
    @Test
    fun `does not request start when permission is missing`() {
        assertFalse(
            ForegroundServiceStartPolicy.shouldRequestStart(
                shouldRun = true,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `does not request start when it should not run`() {
        assertFalse(
            ForegroundServiceStartPolicy.shouldRequestStart(
                shouldRun = false,
                permissionGranted = true,
            ),
        )
    }

    @Test
    fun `requests start when it should run and permission is granted`() {
        assertTrue(
            ForegroundServiceStartPolicy.shouldRequestStart(
                shouldRun = true,
                permissionGranted = true,
            ),
        )
    }

    @Test
    fun `skip reason names disabled before permission`() {
        assertEquals(
            "disabled",
            ForegroundServiceStartPolicy.skipStartReason(
                shouldRun = false,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `skip reason names missing permission when should run`() {
        assertEquals(
            "permission_missing",
            ForegroundServiceStartPolicy.skipStartReason(
                shouldRun = true,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `skip reason is null when start should be requested`() {
        assertNull(
            ForegroundServiceStartPolicy.skipStartReason(
                shouldRun = true,
                permissionGranted = true,
            ),
        )
    }

    @Test
    fun `foreground start denial is not treated as started`() {
        val started =
            ForegroundServiceStartPolicy.tryStartForeground {
                throw SecurityException("foreground service denied")
            }

        assertFalse(started)
    }

    @Test
    fun `background foreground start rejection is not treated as started`() {
        val started =
            ForegroundServiceStartPolicy.tryStartForeground {
                // ForegroundServiceStartNotAllowedException is an IllegalStateException.
                throw IllegalStateException("startForeground not allowed from background")
            }

        assertFalse(started)
    }

    @Test
    fun `foreground start success is treated as started`() {
        val started = ForegroundServiceStartPolicy.tryStartForeground {}

        assertTrue(started)
    }
}

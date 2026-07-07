package com.superdash.kiosk.ui

import com.superdash.doorbell.DoorbellConfig
import com.superdash.doorbell.DoorbellState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorbellOverlayPlaybackPolicyTest {
    private val config =
        DoorbellConfig(
            id = "front",
            name = "Front",
            triggerEntity = "binary_sensor.front_door",
            cameraEntity = "camera.front_door",
        )

    @Test
    fun `showing doorbell starts stream only for foreground activity`() {
        val state = DoorbellState.Showing(config = config, openedAtEpochMs = 123L)

        assertTrue(shouldStartDoorbellStream(state, activityForeground = true))
        assertFalse(shouldStartDoorbellStream(state, activityForeground = false))
    }

    @Test
    fun `idle doorbell never starts stream`() {
        assertFalse(shouldStartDoorbellStream(DoorbellState.Idle, activityForeground = true))
        assertFalse(shouldStartDoorbellStream(DoorbellState.Idle, activityForeground = false))
    }

    @Test
    fun `only settings stream starts when main is paused and settings is foreground`() {
        val state = DoorbellState.Showing(config = config, openedAtEpochMs = 456L)

        val mainMayStartStream = shouldStartDoorbellStream(state, activityForeground = false)
        val settingsMayStartStream = shouldStartDoorbellStream(state, activityForeground = true)

        assertFalse(mainMayStartStream)
        assertTrue(settingsMayStartStream)
    }
}

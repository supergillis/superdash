package com.superdash.kiosk.ui

import com.superdash.feed.FeedConfig
import com.superdash.feed.FeedState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedOverlayPlaybackPolicyTest {
    private val config =
        FeedConfig(
            id = "front",
            name = "Front",
            triggerEntity = "binary_sensor.front_door",
            cameraEntity = "camera.front_door",
        )

    @Test
    fun `showing feed starts stream only for foreground activity`() {
        val state = FeedState.Showing(config = config, openedAtEpochMs = 123L)

        assertTrue(shouldStartFeedStream(state, activityForeground = true))
        assertFalse(shouldStartFeedStream(state, activityForeground = false))
    }

    @Test
    fun `idle feed never starts stream`() {
        assertFalse(shouldStartFeedStream(FeedState.Idle, activityForeground = true))
        assertFalse(shouldStartFeedStream(FeedState.Idle, activityForeground = false))
    }

    @Test
    fun `only settings stream starts when main is paused and settings is foreground`() {
        val state = FeedState.Showing(config = config, openedAtEpochMs = 456L)

        val mainMayStartStream = shouldStartFeedStream(state, activityForeground = false)
        val settingsMayStartStream = shouldStartFeedStream(state, activityForeground = true)

        assertFalse(mainMayStartStream)
        assertTrue(settingsMayStartStream)
    }
}

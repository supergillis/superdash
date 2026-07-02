package com.superdash.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDestinationTest {
    @Test fun `top level destinations match feature module grouping`() {
        assertEquals(
            listOf(
                "Home Assistant",
                "Kiosk",
                "Sidebar",
                "Voice",
                "Screensaver",
                "Doorbell",
                "ESPHome",
                "Admin",
            ),
            SettingsDestination.topLevel.map { destination -> destination.title },
        )
    }

    @Test fun `voice children are focused workflow pages`() {
        assertEquals(
            listOf(
                "Speech pipeline",
                "Local models",
                "Command recording",
                "Advanced tuning",
            ),
            SettingsDestination.voiceChildren.map { destination -> destination.title },
        )
    }

    @Test fun `screensaver nests immich photos`() {
        assertEquals(SettingsDestination.TopLevel.Screensaver, SettingsDestination.ImmichPhotos.parent)
        assertEquals("Immich photos", SettingsDestination.ImmichPhotos.title)
    }

    @Test fun `nested destinations are one level below top level`() {
        val nestedDestinations = SettingsDestination.voiceChildren + SettingsDestination.ImmichPhotos

        assertTrue(nestedDestinations.all { destination -> destination.parent in SettingsDestination.topLevel })
    }
}

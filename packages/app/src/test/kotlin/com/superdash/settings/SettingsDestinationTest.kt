package com.superdash.settings

import com.superdash.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDestinationTest {
    @Test fun `top level destinations match feature module grouping`() {
        assertEquals(
            listOf(
                R.string.settings_general_title,
                R.string.settings_home_assistant_title,
                R.string.settings_kiosk_title,
                R.string.settings_sidebar_title,
                R.string.settings_voice_title,
                R.string.settings_screensaver_title,
                R.string.settings_doorbell_title,
                R.string.settings_esphome_title,
                R.string.settings_admin_title,
            ),
            SettingsDestination.topLevel.map { destination -> destination.titleRes },
        )
    }

    @Test fun `voice children are focused workflow pages`() {
        assertEquals(
            listOf(
                R.string.settings_voice_speech_pipeline_title,
                R.string.settings_voice_local_models_title,
                R.string.settings_voice_command_recording_title,
                R.string.settings_voice_advanced_tuning_title,
            ),
            SettingsDestination.voiceChildren.map { destination -> destination.titleRes },
        )
    }

    @Test fun `screensaver nests immich photos`() {
        assertEquals(SettingsDestination.TopLevel.Screensaver, SettingsDestination.ImmichPhotos.parent)
        assertEquals(R.string.settings_immich_photos_title, SettingsDestination.ImmichPhotos.titleRes)
    }

    @Test fun `nested destinations are one level below top level`() {
        val nestedDestinations = SettingsDestination.voiceChildren + SettingsDestination.ImmichPhotos

        assertTrue(nestedDestinations.all { destination -> destination.parent in SettingsDestination.topLevel })
    }
}

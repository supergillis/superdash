package com.superdash.settings

import com.superdash.screensaver.ScreensaverMode
import com.superdash.settings.ui.shouldShowScreensaverImmichSettings
import com.superdash.settings.ui.shouldShowScreensaverMediaLibrarySettings
import com.superdash.settings.ui.shouldShowScreensaverSharedSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    @Test fun `compact layout starts at top level menu`() {
        assertNull(SettingsLayout.initialDestination(isWide = false))
    }

    @Test fun `wide layout starts on home assistant`() {
        assertEquals(
            SettingsDestination.TopLevel.HomeAssistant,
            SettingsLayout.initialDestination(isWide = true),
        )
    }

    @Test fun `child destination selects parent in navigation`() {
        assertEquals(
            SettingsDestination.TopLevel.Voice,
            SettingsLayout.selectedTopLevel(SettingsDestination.Child.VoiceSpeechPipeline),
        )
    }

    @Test fun `navigating up from child returns to parent`() {
        assertEquals(
            SettingsDestination.TopLevel.Screensaver,
            SettingsLayout.navigateUp(SettingsDestination.Child.ImmichPhotos),
        )
    }

    @Test fun `navigating up from top level returns to menu`() {
        assertNull(SettingsLayout.navigateUp(SettingsDestination.TopLevel.Esphome))
    }

    @Test fun `navigation test tags are stable`() {
        assertEquals(
            "settings_nav_home_assistant",
            SettingsLayout.navigationTestTag(SettingsDestination.TopLevel.HomeAssistant),
        )
        assertEquals(
            "settings_nav_esphome",
            SettingsLayout.navigationTestTag(SettingsDestination.TopLevel.Esphome),
        )
    }

    @Test fun `screensaver shared rows show for day off and night black`() {
        assertTrue(shouldShowScreensaverSharedSettings(ScreensaverMode.Off, ScreensaverMode.Black))
        assertFalse(shouldShowScreensaverMediaLibrarySettings(ScreensaverMode.Off, ScreensaverMode.Black))
        assertFalse(shouldShowScreensaverImmichSettings(ScreensaverMode.Off, ScreensaverMode.Black))
    }

    @Test fun `screensaver shared rows show for day off and night clock`() {
        assertTrue(shouldShowScreensaverSharedSettings(ScreensaverMode.Off, ScreensaverMode.Clock))
        assertFalse(shouldShowScreensaverMediaLibrarySettings(ScreensaverMode.Off, ScreensaverMode.Clock))
        assertFalse(shouldShowScreensaverImmichSettings(ScreensaverMode.Off, ScreensaverMode.Clock))
    }

    @Test fun `screensaver immich row shows for day off and night immich`() {
        assertTrue(shouldShowScreensaverSharedSettings(ScreensaverMode.Off, ScreensaverMode.Immich))
        assertFalse(shouldShowScreensaverMediaLibrarySettings(ScreensaverMode.Off, ScreensaverMode.Immich))
        assertTrue(shouldShowScreensaverImmichSettings(ScreensaverMode.Off, ScreensaverMode.Immich))
    }

    @Test fun `screensaver rows hide when day and night are off`() {
        assertFalse(shouldShowScreensaverSharedSettings(ScreensaverMode.Off, ScreensaverMode.Off))
        assertFalse(shouldShowScreensaverMediaLibrarySettings(ScreensaverMode.Off, ScreensaverMode.Off))
        assertFalse(shouldShowScreensaverImmichSettings(ScreensaverMode.Off, ScreensaverMode.Off))
    }
}

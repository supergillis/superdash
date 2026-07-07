package com.superdash.kiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarShortcutActionTest {
    @Test
    fun `show screensaver shortcut does not emit user touch`() {
        assertFalse(SidebarAction.ShowScreensaver.emitsUserTouchedFromSidebar())
    }

    @Test
    fun `dismiss screensaver shortcut touches idle directly`() {
        assertTrue(SidebarAction.DismissScreensaver.touchesIdleFromSidebar())
    }

    @Test
    fun `ordinary shortcuts still emit user touch`() {
        assertTrue(SidebarAction.OpenSettings.emitsUserTouchedFromSidebar())
        assertTrue(SidebarAction.ReloadDashboard.emitsUserTouchedFromSidebar())
        assertTrue(SidebarAction.SetNightModeActive(active = true).emitsUserTouchedFromSidebar())
        assertTrue(SidebarAction.OpenDashboardPath("lovelace/home").emitsUserTouchedFromSidebar())
    }

    @Test
    fun `night mode shortcut is selected only when night mode is active`() {
        val shortcut =
            SidebarShortcut(
                id = "night",
                title = "Night mode on",
                icon = "moon",
                action = SidebarAction.SetNightModeActive(active = true),
            )

        assertTrue(shortcut.selectedInSidebar(nightModeActive = true))
        assertFalse(shortcut.selectedInSidebar(nightModeActive = false))
    }

    @Test
    fun `day mode shortcut is selected only when night mode is inactive`() {
        val shortcut =
            SidebarShortcut(
                id = "day",
                title = "Night mode off",
                icon = "sun",
                action = SidebarAction.SetNightModeActive(active = false),
            )

        assertTrue(shortcut.selectedInSidebar(nightModeActive = false))
        assertFalse(shortcut.selectedInSidebar(nightModeActive = true))
    }

    @Test
    fun `non mode shortcuts are not selected`() {
        val shortcut =
            SidebarShortcut(
                id = "settings",
                title = "Settings",
                icon = "settings",
                action = SidebarAction.OpenSettings,
            )

        assertFalse(shortcut.selectedInSidebar(nightModeActive = false))
        assertFalse(shortcut.selectedInSidebar(nightModeActive = true))
    }
}

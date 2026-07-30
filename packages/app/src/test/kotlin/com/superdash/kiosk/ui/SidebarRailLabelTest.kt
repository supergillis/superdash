package com.superdash.kiosk.ui

import com.superdash.core.json.coreJson
import com.superdash.kiosk.SidebarAction
import com.superdash.kiosk.SidebarShortcut
import com.superdash.kiosk.defaultShortLabel
import com.superdash.kiosk.emitsUserTouchedFromSidebar
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarRailLabelTest {
    @Test fun `customized blank short label stays blank`() {
        val shortcut =
            SidebarShortcut(
                id = "dashboard-home",
                title = "Home",
                icon = "dashboard",
                shortLabel = null,
                shortLabelCustomized = true,
                action = SidebarAction.OpenDashboardPath("lovelace/home"),
            )

        assertEquals("", sidebarLabel(shortcut))
    }

    @Test fun `generated blank short label uses action default`() {
        val shortcut =
            SidebarShortcut(
                id = "reload-dashboard",
                title = "Reload dashboard",
                icon = "refresh",
                shortLabel = null,
                shortLabelCustomized = false,
                action = SidebarAction.ReloadDashboard,
            )

        assertEquals("Reload", sidebarLabel(shortcut))
    }

    @Test
    fun `show feed action has a default short label`() {
        assertEquals("Camera", SidebarAction.ShowFeed("baby").defaultShortLabel)
    }

    @Test
    fun `show feed action counts as a user touch`() {
        assertEquals(true, SidebarAction.ShowFeed("baby").emitsUserTouchedFromSidebar())
    }

    @Test
    fun `show feed action round trips through shortcut json`() {
        val shortcut =
            SidebarShortcut(
                id = "baby-cam",
                title = "Nursery",
                icon = "camera",
                action = SidebarAction.ShowFeed("baby"),
            )

        val encoded = coreJson.encodeToString(SidebarShortcut.serializer(), shortcut)
        val decoded = coreJson.decodeFromString(SidebarShortcut.serializer(), encoded)

        assertEquals(shortcut, decoded)
    }
}

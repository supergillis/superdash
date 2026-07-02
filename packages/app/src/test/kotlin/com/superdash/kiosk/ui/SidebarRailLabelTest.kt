package com.superdash.kiosk.ui

import com.superdash.kiosk.SidebarAction
import com.superdash.kiosk.SidebarShortcut
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
}

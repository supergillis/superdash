package com.superdash.settings.ui

import com.superdash.kiosk.SidebarAction
import com.superdash.kiosk.SidebarShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SidebarSettingsSectionTest {
    @Test fun `generated draft changes from view to reload when action changes to reload dashboard`() {
        val draft =
            updatedLabelDraftForActionChange(
                draft = SidebarLabelDraft(value = "View", customized = false),
                selectedAction = SidebarAction.ReloadDashboard,
            )

        assertEquals(SidebarLabelDraft(value = "Reload", customized = false), draft)
    }

    @Test fun `customized draft stays view when action changes to reload dashboard`() {
        val draft =
            updatedLabelDraftForActionChange(
                draft = SidebarLabelDraft(value = "View", customized = true),
                selectedAction = SidebarAction.ReloadDashboard,
            )

        assertEquals(SidebarLabelDraft(value = "View", customized = true), draft)
    }

    @Test fun `blank short label saves null`() {
        val shortLabel =
            savedShortLabel(SidebarLabelDraft(value = "  ", customized = true))

        assertEquals(null, shortLabel)
    }

    @Test fun `blank customized label saves null and remains customized`() {
        val shortcut =
            savedSidebarShortcut(
                shortcut =
                    SidebarShortcut(
                        id = "dashboard-1",
                        title = "Dashboard 1",
                        icon = "dashboard",
                        action = SidebarAction.OpenDashboardPath("lovelace/1"),
                    ),
                titleDraft = "Dashboard 1",
                shortLabelDraft = SidebarLabelDraft(value = "  ", customized = true),
                iconDraft = "dashboard",
                selectedAction = SidebarAction.ReloadDashboard,
            )

        assertEquals(null, shortcut.shortLabel)
        assertEquals(true, shortcut.shortLabelCustomized)
    }

    @Test fun `new dashboard shortcut uses view short label`() {
        val shortcuts = emptyList<SidebarShortcut>()
        val nextId = nextDashboardShortcutId(shortcuts)
        val shortcut = newDashboardShortcut(nextId)

        assertEquals("Dashboard 1", shortcut.title)
        assertEquals("View", shortcut.shortLabel)
        assertEquals(false, shortcut.shortLabelCustomized)
        assertEquals(SidebarAction.OpenDashboardPath("lovelace/1"), shortcut.action)
    }

    @Test fun `next dashboard shortcut id skips existing suffixes`() {
        val shortcuts =
            listOf(
                SidebarShortcut(
                    id = "dashboard-2",
                    title = "Dashboard 2",
                    icon = "dashboard",
                    action = SidebarAction.OpenDashboardPath("lovelace/2"),
                ),
                SidebarShortcut(
                    id = "dashboard-4",
                    title = "Dashboard 4",
                    icon = "dashboard",
                    action = SidebarAction.OpenDashboardPath("lovelace/4"),
                ),
            )

        assertEquals("dashboard-5", nextDashboardShortcutId(shortcuts))
    }

    @Test fun `settings action choices omit dismiss screensaver`() {
        assertFalse(sidebarActionChoiceLabels().contains("Dismiss screensaver"))
    }

    @Test fun `settings icon choices omit wake`() {
        assertFalse(sidebarIconChoiceLabels().contains("Wake"))
    }
}

package com.superdash.settings

import com.superdash.core.persistence.InMemoryKeyValueStore
import com.superdash.kiosk.SidebarAction
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarSettingsDefaults
import com.superdash.kiosk.SidebarShortcut
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositorySidebarSettingsTest {
    @Test fun `position defaults to left`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            assertEquals(SidebarPosition.Left, settings.position.first())
        }

    @Test fun `pinned defaults to false`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            assertEquals(false, settings.pinned.first())
        }

    @Test fun `showLabels defaults to false`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            assertEquals(false, settings.showLabels.first())
        }

    @Test fun `setShowLabels stores show label value`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            settings.setShowLabels(true)
            assertEquals(true, settings.showLabels.first())
        }

    @Test fun `shortcuts default to local command list`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            assertEquals(
                listOf(
                    SidebarAction.OpenSettings,
                    SidebarAction.ShowScreensaver,
                    SidebarAction.SetNightModeActive(true),
                    SidebarAction.SetNightModeActive(false),
                    SidebarAction.ReloadDashboard,
                ),
                settings.shortcuts.first().map { shortcut -> shortcut.action },
            )
        }

    @Test fun `default shortcuts include short labels`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            assertEquals(
                listOf("Settings", "Sleep", "Night", "Day", "Reload"),
                settings.shortcuts.first().map { shortcut -> shortcut.shortLabel },
            )
        }

    @Test fun `legacy shortcut json receives action based short labels`() =
        runTest {
            val store = InMemoryKeyValueStore()
            store.set(
                "sidebar.shortcuts",
                """
                [
                  {"id":"settings","title":"Settings","icon":"settings","action":{"type":"open_settings"}},
                  {"id":"show-screensaver","title":"Show screensaver","icon":"screensaver","action":{"type":"show_screensaver"}},
                  {"id":"dismiss-screensaver","title":"Dismiss screensaver","icon":"wake","action":{"type":"dismiss_screensaver"}},
                  {"id":"dashboard-home","title":"Home","icon":"dashboard","action":{"type":"open_dashboard_path","path":"lovelace/home"}}
                ]
                """.trimIndent(),
            )
            val settings = SettingsRepositorySidebarSettings(store)
            assertEquals(
                listOf("Settings", "Sleep", "View"),
                settings.shortcuts.first().map { shortcut -> shortcut.shortLabel },
            )
        }

    @Test fun `stored dismiss screensaver shortcut is removed`() =
        runTest {
            val store = InMemoryKeyValueStore()
            store.set(
                "sidebar.shortcuts",
                """
                [
                  {"id":"settings","title":"Settings","icon":"settings","action":{"type":"open_settings"}},
                  {"id":"dismiss-screensaver","title":"Dismiss screensaver","icon":"wake","action":{"type":"dismiss_screensaver"}},
                  {"id":"reload-dashboard","title":"Reload dashboard","icon":"refresh","action":{"type":"reload_dashboard"}}
                ]
                """.trimIndent(),
            )
            val settings = SettingsRepositorySidebarSettings(store)

            assertEquals(
                listOf(SidebarAction.OpenSettings, SidebarAction.ReloadDashboard),
                settings.shortcuts.first().map { shortcut -> shortcut.action },
            )
        }

    @Test fun `legacy custom short label without customization flag is preserved`() =
        runTest {
            val store = InMemoryKeyValueStore()
            store.set(
                "sidebar.shortcuts",
                """
                [
                  {"id":"dashboard-home","title":"Home","icon":"dashboard","shortLabel":"Home","action":{"type":"open_dashboard_path","path":"lovelace/home"}}
                ]
                """.trimIndent(),
            )
            val settings = SettingsRepositorySidebarSettings(store)

            val shortcut = settings.shortcuts.first()[1]
            assertEquals("Home", shortcut.shortLabel)
            assertEquals(true, shortcut.shortLabelCustomized)
        }

    @Test fun `legacy generated short label without customization flag normalizes to action default`() =
        runTest {
            val store = InMemoryKeyValueStore()
            store.set(
                "sidebar.shortcuts",
                """
                [
                  {"id":"reload-dashboard","title":"Reload dashboard","icon":"refresh","shortLabel":"View","action":{"type":"reload_dashboard"}}
                ]
                """.trimIndent(),
            )
            val settings = SettingsRepositorySidebarSettings(store)

            val shortcut = settings.shortcuts.first()[1]
            assertEquals("Reload", shortcut.shortLabel)
            assertEquals(false, shortcut.shortLabelCustomized)
        }

    @Test fun `position normalizes unknown stored value to left`() =
        runTest {
            val store = InMemoryKeyValueStore()
            store.set("sidebar.position", "sideways")
            val settings = SettingsRepositorySidebarSettings(store)
            assertEquals(SidebarPosition.Left, settings.position.first())
        }

    @Test fun `setPosition stores normalized position`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            settings.setPosition(SidebarPosition.Bottom)
            assertEquals(SidebarPosition.Bottom, settings.position.first())
        }

    @Test fun `setPinned stores pinned value`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            settings.setPinned(true)
            assertEquals(true, settings.pinned.first())
        }

    @Test fun `shortcuts round trip through json`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            val shortcuts =
                listOf(
                    SidebarShortcut(
                        id = "dashboard-home",
                        title = "Home",
                        icon = "dashboard",
                        action = SidebarAction.OpenDashboardPath("lovelace/home"),
                    ),
                )

            settings.setShortcuts(shortcuts)

            assertEquals(
                listOf(SidebarSettingsDefaults.settingsShortcut) +
                    shortcuts.map { shortcut ->
                        shortcut.copy(shortLabel = "View")
                    },
                settings.shortcuts.first(),
            )
        }

    @Test fun `shortcuts preserve trimmed custom short labels`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            val shortcuts =
                listOf(
                    SidebarShortcut(
                        id = "dashboard-home",
                        title = "Home",
                        icon = "dashboard",
                        shortLabel = "  Home  ",
                        shortLabelCustomized = true,
                        action = SidebarAction.OpenDashboardPath("lovelace/home"),
                    ),
                )

            settings.setShortcuts(shortcuts)

            assertEquals("Home", settings.shortcuts.first()[1].shortLabel)
        }

    @Test fun `stale generated short label normalizes to selected action default`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            val shortcuts =
                listOf(
                    SidebarShortcut(
                        id = "reload-dashboard",
                        title = "Reload dashboard",
                        icon = "refresh",
                        shortLabel = "View",
                        shortLabelCustomized = false,
                        action = SidebarAction.ReloadDashboard,
                    ),
                )

            settings.setShortcuts(shortcuts)

            assertEquals("Reload", settings.shortcuts.first()[1].shortLabel)
        }

    @Test fun `customized blank short label round trips without default normalization`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            val shortcuts =
                listOf(
                    SidebarShortcut(
                        id = "dashboard-home",
                        title = "Home",
                        icon = "dashboard",
                        shortLabel = null,
                        shortLabelCustomized = true,
                        action = SidebarAction.OpenDashboardPath("lovelace/home"),
                    ),
                )

            settings.setShortcuts(shortcuts)

            val shortcut = settings.shortcuts.first()[1]
            assertEquals(null, shortcut.shortLabel)
            assertEquals(true, shortcut.shortLabelCustomized)
        }

    @Test fun `invalid shortcut json falls back to defaults`() =
        runTest {
            val store = InMemoryKeyValueStore()
            store.set("sidebar.shortcuts", "{not json")
            val settings = SettingsRepositorySidebarSettings(store)
            assertEquals(SidebarSettingsDefaults.shortcuts, settings.shortcuts.first())
        }

    @Test fun `shortcuts restore settings shortcut when missing from stored value`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            val shortcuts =
                listOf(
                    SidebarShortcut(
                        id = "dashboard-home",
                        title = "Home",
                        icon = "dashboard",
                        action = SidebarAction.OpenDashboardPath("lovelace/home"),
                    ),
                )

            settings.setShortcuts(shortcuts)

            assertEquals(
                listOf(SidebarAction.OpenSettings, SidebarAction.OpenDashboardPath("lovelace/home")),
                settings.shortcuts.first().map { shortcut -> shortcut.action },
            )
        }

    @Test fun `shortcuts collapse duplicate settings shortcuts`() =
        runTest {
            val settings = SettingsRepositorySidebarSettings(InMemoryKeyValueStore())
            val shortcuts =
                listOf(
                    SidebarShortcut(
                        id = "settings",
                        title = "Settings",
                        icon = "settings",
                        action = SidebarAction.OpenSettings,
                    ),
                    SidebarShortcut(
                        id = "settings-copy",
                        title = "More settings",
                        icon = "settings",
                        action = SidebarAction.OpenSettings,
                    ),
                )

            settings.setShortcuts(shortcuts)

            assertEquals(
                listOf(SidebarAction.OpenSettings),
                settings.shortcuts.first().map { shortcut -> shortcut.action },
            )
        }
}

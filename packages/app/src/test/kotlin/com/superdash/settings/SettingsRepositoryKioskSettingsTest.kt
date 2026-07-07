package com.superdash.settings

import com.superdash.core.persistence.InMemoryKeyValueStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryKioskSettingsTest {
    @Test
    fun `keepScreenOn defaults to true`() =
        runTest {
            val settings = SettingsRepositoryKioskSettings(InMemoryKeyValueStore())
            assertEquals(true, settings.keepScreenOn.first())
        }

    @Test
    fun `startOnBoot defaults to true`() =
        runTest {
            val settings = SettingsRepositoryKioskSettings(InMemoryKeyValueStore())
            assertEquals(true, settings.startOnBoot.first())
        }

    @Test
    fun `launchOnWake defaults to false`() =
        runTest {
            val settings = SettingsRepositoryKioskSettings(InMemoryKeyValueStore())
            assertEquals(false, settings.launchOnWake.first())
        }

    @Test
    fun `esphomeEnabled defaults to false`() =
        runTest {
            val settings = SettingsRepositoryKioskSettings(InMemoryKeyValueStore())
            assertEquals(false, settings.esphomeEnabled.first())
        }

    @Test
    fun `dashboardPath trims whitespace and slashes on read`() =
        runTest {
            val store = InMemoryKeyValueStore()
            store.set("dashboard_path", "  /lovelace/0/  ")
            val settings = SettingsRepositoryKioskSettings(store)
            assertEquals("lovelace/0", settings.dashboardPath.first())
        }

    @Test
    fun `dashboardPath trims whitespace and slashes on write`() =
        runTest {
            val store = InMemoryKeyValueStore()
            val settings = SettingsRepositoryKioskSettings(store)
            settings.setDashboardPath("  /lovelace/0/  ")
            assertEquals("lovelace/0", settings.dashboardPath.first())
        }
}

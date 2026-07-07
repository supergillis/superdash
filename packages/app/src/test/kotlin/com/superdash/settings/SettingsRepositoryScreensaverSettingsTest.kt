package com.superdash.settings

import com.superdash.core.persistence.InMemoryKeyValueStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRepositoryScreensaverSettingsTest {
    @Test
    fun `dayMode defaults to photos`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            assertEquals("photos", settings.dayMode.first())
        }

    @Test
    fun `nightMode defaults to black`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            assertEquals("black", settings.nightMode.first())
        }

    @Test
    fun `idleTimeoutSec defaults to 300`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            assertEquals(300, settings.idleTimeoutSec.first())
        }

    @Test
    fun `pictureSpacingDp coerces below zero to zero`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            settings.setPictureSpacingDp(-5)
            assertEquals(0, settings.pictureSpacingDp.first())
        }

    @Test
    fun `pictureSpacingDp coerces above 48 to 48`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            settings.setPictureSpacingDp(100)
            assertEquals(48, settings.pictureSpacingDp.first())
        }

    @Test
    fun `mediaLibrarySource pairs id and title atomically`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            settings.setMediaLibrarySource("source-1", "Title 1")
            assertEquals("source-1", settings.mediaLibrarySourceId.first())
            assertEquals("Title 1", settings.mediaLibrarySourceTitle.first())
        }

    @Test
    fun `mediaLibrarySource null id clears both`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            settings.setMediaLibrarySource("source-1", "Title 1")
            settings.setMediaLibrarySource(null, null)
            assertNull(settings.mediaLibrarySourceId.first())
            assertNull(settings.mediaLibrarySourceTitle.first())
        }

    @Test
    fun `weatherEntityId defaults to weather dot home`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            assertEquals("weather.home", settings.weatherEntityId.first())
        }

    @Test
    fun `calendarEntityId defaults to empty`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            assertEquals("", settings.calendarEntityId.first())
        }

    @Test
    fun `calendarEntityId round-trip`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            settings.setCalendarEntityId("calendar.family")
            assertEquals("calendar.family", settings.calendarEntityId.first())
        }

    @Test
    fun `powerUsageEntityId defaults to empty`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            assertEquals("", settings.powerUsageEntityId.first())
        }

    @Test
    fun `powerUsageEntityId round-trip`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            settings.setPowerUsageEntityId("sensor.house_power")
            assertEquals("sensor.house_power", settings.powerUsageEntityId.first())
        }

    @Test
    fun `solarPowerEntityId defaults to empty`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            assertEquals("", settings.solarPowerEntityId.first())
        }

    @Test
    fun `solarPowerEntityId round-trip`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            settings.setSolarPowerEntityId("sensor.solar_power")
            assertEquals("sensor.solar_power", settings.solarPowerEntityId.first())
        }

    @Test
    fun `gridPowerEntityId defaults to empty`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            assertEquals("", settings.gridPowerEntityId.first())
        }

    @Test
    fun `gridPowerEntityId round-trip`() =
        runTest {
            val settings = SettingsRepositoryScreensaverSettings(InMemoryKeyValueStore())
            settings.setGridPowerEntityId("sensor.grid_power")
            assertEquals("sensor.grid_power", settings.gridPowerEntityId.first())
        }
}

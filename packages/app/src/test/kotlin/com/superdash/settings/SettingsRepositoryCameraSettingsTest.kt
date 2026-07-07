package com.superdash.settings

import com.superdash.core.persistence.InMemoryKeyValueStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryCameraSettingsTest {
    @Test
    fun `enabled defaults to false`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            assertEquals(false, settings.enabled.first())
        }

    @Test
    fun `facing defaults to front`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            assertEquals("front", settings.facing.first())
        }

    @Test
    fun `resolution defaults to 1280x720`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            assertEquals("1280x720", settings.resolution.first())
        }

    @Test
    fun `jpeg quality defaults to 60`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            assertEquals(60, settings.jpegQuality.first())
        }

    @Test
    fun `jpeg quality coerces below 1 to 1`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            settings.setJpegQuality(-5)
            assertEquals(1, settings.jpegQuality.first())
        }

    @Test
    fun `jpeg quality coerces above 100 to 100`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            settings.setJpegQuality(999)
            assertEquals(100, settings.jpegQuality.first())
        }

    @Test
    fun `motion mode defaults to motion`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            assertEquals("motion", settings.motionMode.first())
        }

    @Test
    fun `motion sensitivity defaults to 50`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            assertEquals(50, settings.motionSensitivity.first())
        }

    @Test
    fun `motion sensitivity coerces below 0 to 0`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            settings.setMotionSensitivity(-5)
            assertEquals(0, settings.motionSensitivity.first())
        }

    @Test
    fun `motion sensitivity coerces above 100 to 100`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            settings.setMotionSensitivity(999)
            assertEquals(100, settings.motionSensitivity.first())
        }

    @Test
    fun `motion clear delay defaults to 15 seconds`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            assertEquals(15, settings.motionClearDelaySec.first())
        }

    @Test
    fun `motion clear delay coerces below 0 to 0`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            settings.setMotionClearDelaySec(-5)
            assertEquals(0, settings.motionClearDelaySec.first())
        }

    @Test
    fun `motion clear delay coerces above 120 to 120`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            settings.setMotionClearDelaySec(999)
            assertEquals(120, settings.motionClearDelaySec.first())
        }

    @Test
    fun `wake on motion defaults to false`() =
        runTest {
            val settings = SettingsRepositoryCameraSettings(InMemoryKeyValueStore())
            assertEquals(false, settings.wakeOnMotion.first())
        }
}

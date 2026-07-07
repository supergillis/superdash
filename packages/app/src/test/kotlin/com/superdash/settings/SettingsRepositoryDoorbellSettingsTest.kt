package com.superdash.settings

import com.superdash.core.persistence.InMemoryKeyValueStore
import com.superdash.doorbell.DoorbellConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryDoorbellSettingsTest {
    @Test
    fun `enabled defaults to false`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            assertEquals(false, settings.enabled.first())
        }

    @Test
    fun `auto close defaults to 60 seconds`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            assertEquals(60, settings.autoCloseSec.first())
        }

    @Test
    fun `auto close coerces below zero to zero`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            settings.setAutoCloseSec(-5)
            assertEquals(0, settings.autoCloseSec.first())
        }

    @Test
    fun `auto close coerces above 300 to 300`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            settings.setAutoCloseSec(999)
            assertEquals(300, settings.autoCloseSec.first())
        }

    @Test
    fun `doorbells defaults to empty list`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            assertEquals(emptyList<DoorbellConfig>(), settings.doorbells.first())
        }

    @Test
    fun `upsert appends when id is new`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            val config =
                DoorbellConfig(
                    id = "front",
                    name = "Front",
                    triggerEntity = "binary_sensor.front",
                    cameraEntity = "camera.front",
                )
            settings.upsertDoorbell(config)
            assertEquals(listOf(config), settings.doorbells.first())
        }

    @Test
    fun `upsert replaces when id exists, preserving order`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            val a =
                DoorbellConfig(
                    id = "a",
                    name = "A",
                    triggerEntity = "binary_sensor.a",
                    cameraEntity = "camera.a",
                )
            val b =
                DoorbellConfig(
                    id = "b",
                    name = "B",
                    triggerEntity = "binary_sensor.b",
                    cameraEntity = "camera.b",
                )
            settings.upsertDoorbell(a)
            settings.upsertDoorbell(b)
            val aRenamed = a.copy(name = "A2")
            settings.upsertDoorbell(aRenamed)
            assertEquals(listOf(aRenamed, b), settings.doorbells.first())
        }

    @Test
    fun `remove drops by id`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            val a =
                DoorbellConfig(
                    id = "a",
                    name = "A",
                    triggerEntity = "binary_sensor.a",
                    cameraEntity = "camera.a",
                )
            settings.upsertDoorbell(a)
            settings.removeDoorbell("a")
            assertEquals(emptyList<DoorbellConfig>(), settings.doorbells.first())
        }

    @Test
    fun `concurrent upserts keep unrelated doorbells`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            val configs =
                listOf(
                    DoorbellConfig(
                        id = "front",
                        name = "Front",
                        triggerEntity = "binary_sensor.front",
                        cameraEntity = "camera.front",
                    ),
                    DoorbellConfig(
                        id = "side",
                        name = "Side",
                        triggerEntity = "binary_sensor.side",
                        cameraEntity = "camera.side",
                    ),
                    DoorbellConfig(
                        id = "back",
                        name = "Back",
                        triggerEntity = "binary_sensor.back",
                        cameraEntity = "camera.back",
                    ),
                )

            configs
                .map { config -> async(Dispatchers.Default) { settings.upsertDoorbell(config) } }
                .awaitAll()

            assertEquals(
                configs.map { config -> config.id }.toSet(),
                settings.doorbells
                    .first()
                    .map { config -> config.id }
                    .toSet(),
            )
        }

    @Test
    fun `concurrent upsert and remove keeps ordered mutations`() =
        runTest {
            val settings = SettingsRepositoryDoorbellSettings(InMemoryKeyValueStore())
            val first =
                DoorbellConfig(
                    id = "first",
                    name = "First",
                    triggerEntity = "binary_sensor.first",
                    cameraEntity = "camera.first",
                )
            val removed =
                DoorbellConfig(
                    id = "removed",
                    name = "Removed",
                    triggerEntity = "binary_sensor.removed",
                    cameraEntity = "camera.removed",
                )
            val added =
                DoorbellConfig(
                    id = "added",
                    name = "Added",
                    triggerEntity = "binary_sensor.added",
                    cameraEntity = "camera.added",
                )
            settings.upsertDoorbell(first)
            settings.upsertDoorbell(removed)

            awaitAll(
                async(Dispatchers.Default) { settings.removeDoorbell("removed") },
                async(Dispatchers.Default) { settings.upsertDoorbell(added) },
            )

            assertEquals(
                setOf("first", "added"),
                settings.doorbells
                    .first()
                    .map { config -> config.id }
                    .toSet(),
            )
        }
}

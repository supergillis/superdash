package com.superdash.settings

import com.superdash.core.persistence.InMemoryKeyValueStore
import com.superdash.feed.FeedConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryFeedSettingsTest {
    @Test
    fun `enabled defaults to false`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            assertEquals(false, settings.enabled.first())
        }

    @Test
    fun `auto close defaults to 60 seconds`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            assertEquals(60, settings.autoCloseSec.first())
        }

    @Test
    fun `auto close coerces below zero to zero`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            settings.setAutoCloseSec(-5)
            assertEquals(0, settings.autoCloseSec.first())
        }

    @Test
    fun `auto close coerces above 300 to 300`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            settings.setAutoCloseSec(999)
            assertEquals(300, settings.autoCloseSec.first())
        }

    @Test
    fun `feeds defaults to empty list`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            assertEquals(emptyList<FeedConfig>(), settings.feeds.first())
        }

    @Test
    fun `upsert appends when id is new`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            val config =
                FeedConfig(
                    id = "front",
                    name = "Front",
                    triggerEntity = "binary_sensor.front",
                    cameraEntity = "camera.front",
                )
            settings.upsertFeed(config)
            assertEquals(listOf(config), settings.feeds.first())
        }

    @Test
    fun `upsert replaces when id exists, preserving order`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            val a =
                FeedConfig(
                    id = "a",
                    name = "A",
                    triggerEntity = "binary_sensor.a",
                    cameraEntity = "camera.a",
                )
            val b =
                FeedConfig(
                    id = "b",
                    name = "B",
                    triggerEntity = "binary_sensor.b",
                    cameraEntity = "camera.b",
                )
            settings.upsertFeed(a)
            settings.upsertFeed(b)
            val aRenamed = a.copy(name = "A2")
            settings.upsertFeed(aRenamed)
            assertEquals(listOf(aRenamed, b), settings.feeds.first())
        }

    @Test
    fun `remove drops by id`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            val a =
                FeedConfig(
                    id = "a",
                    name = "A",
                    triggerEntity = "binary_sensor.a",
                    cameraEntity = "camera.a",
                )
            settings.upsertFeed(a)
            settings.removeFeed("a")
            assertEquals(emptyList<FeedConfig>(), settings.feeds.first())
        }

    @Test
    fun `concurrent upserts keep unrelated feeds`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            val configs =
                listOf(
                    FeedConfig(
                        id = "front",
                        name = "Front",
                        triggerEntity = "binary_sensor.front",
                        cameraEntity = "camera.front",
                    ),
                    FeedConfig(
                        id = "side",
                        name = "Side",
                        triggerEntity = "binary_sensor.side",
                        cameraEntity = "camera.side",
                    ),
                    FeedConfig(
                        id = "back",
                        name = "Back",
                        triggerEntity = "binary_sensor.back",
                        cameraEntity = "camera.back",
                    ),
                )

            configs
                .map { config -> async(Dispatchers.Default) { settings.upsertFeed(config) } }
                .awaitAll()

            assertEquals(
                configs.map { config -> config.id }.toSet(),
                settings.feeds
                    .first()
                    .map { config -> config.id }
                    .toSet(),
            )
        }

    @Test
    fun `concurrent upsert and remove keeps ordered mutations`() =
        runTest {
            val settings = SettingsRepositoryFeedSettings(InMemoryKeyValueStore())
            val first =
                FeedConfig(
                    id = "first",
                    name = "First",
                    triggerEntity = "binary_sensor.first",
                    cameraEntity = "camera.first",
                )
            val removed =
                FeedConfig(
                    id = "removed",
                    name = "Removed",
                    triggerEntity = "binary_sensor.removed",
                    cameraEntity = "camera.removed",
                )
            val added =
                FeedConfig(
                    id = "added",
                    name = "Added",
                    triggerEntity = "binary_sensor.added",
                    cameraEntity = "camera.added",
                )
            settings.upsertFeed(first)
            settings.upsertFeed(removed)

            awaitAll(
                async(Dispatchers.Default) { settings.removeFeed("removed") },
                async(Dispatchers.Default) { settings.upsertFeed(added) },
            )

            assertEquals(
                setOf("first", "added"),
                settings.feeds
                    .first()
                    .map { config -> config.id }
                    .toSet(),
            )
        }
}

package com.superdash.feed

import com.superdash.core.json.coreJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedConfigTest {
    private val json = coreJson

    @Test
    fun `round trip preserves all fields`() {
        val config =
            FeedConfig(
                id = "uuid-1",
                name = "Front Door",
                triggerEntity = "binary_sensor.front_door_visitor",
                cameraEntity = "camera.front_door_sub",
            )
        val encoded = json.encodeToString(FeedConfig.serializer(), config)
        val decoded = json.decodeFromString(FeedConfig.serializer(), encoded)
        assertEquals(config, decoded)
    }

    @Test
    fun `decode list round trip preserves order`() {
        val list =
            listOf(
                FeedConfig("a", "A", "binary_sensor.a", "camera.a"),
                FeedConfig("b", "B", "binary_sensor.b", "camera.b"),
            )
        val encoded = FeedConfig.encodeList(list)
        val decoded = FeedConfig.decodeList(encoded)
        assertEquals(list, decoded)
    }

    @Test
    fun `decodeList returns empty on malformed input`() {
        assertEquals(emptyList<FeedConfig>(), FeedConfig.decodeList(""))
        assertEquals(emptyList<FeedConfig>(), FeedConfig.decodeList("{not json"))
        assertEquals(emptyList<FeedConfig>(), FeedConfig.decodeList("null"))
    }

    @Test
    fun `newWith generates a non empty UUID id`() {
        val a = FeedConfig.newWith("X", "binary_sensor.x", "camera.x")
        val b = FeedConfig.newWith("Y", "binary_sensor.y", "camera.y")
        assertTrue(a.id.isNotEmpty())
        assertTrue(b.id.isNotEmpty())
        assertEquals(true, a.id != b.id)
    }

    @Test
    fun `stored pre-rename payload decodes with momentary defaults`() {
        val stored =
            """
            [{"id":"a","name":"Front","triggerEntity":"binary_sensor.front","cameraEntity":"camera.front"}]
            """.trimIndent()

        val decoded = FeedConfig.decodeList(stored)

        assertEquals(1, decoded.size)
        val config = decoded.first()
        assertEquals(FeedTrigger.Momentary, config.trigger)
        assertEquals(60, config.autoCloseSec)
        assertEquals(true, config.wakeScreen)
        assertEquals(0, config.order)
    }

    @Test
    fun `sustained trigger survives an encode decode round trip`() {
        val config =
            FeedConfig(
                id = "b",
                name = "Nursery",
                triggerEntity = "input_boolean.baby_monitor",
                cameraEntity = "camera.nursery",
                trigger = FeedTrigger.Sustained(activeStates = listOf("on", "streaming")),
                autoCloseSec = 0,
                wakeScreen = false,
                order = 5,
            )

        val decoded = FeedConfig.decodeList(FeedConfig.encodeList(listOf(config)))

        assertEquals(listOf(config), decoded)
    }
}

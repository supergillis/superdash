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
}

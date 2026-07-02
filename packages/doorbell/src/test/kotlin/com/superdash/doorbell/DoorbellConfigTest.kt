package com.superdash.doorbell

import com.superdash.core.json.coreJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorbellConfigTest {
    private val json = coreJson

    @Test
    fun `round trip preserves all fields`() {
        val config =
            DoorbellConfig(
                id = "uuid-1",
                name = "Front Door",
                triggerEntity = "binary_sensor.front_door_visitor",
                cameraEntity = "camera.front_door_sub",
            )
        val encoded = json.encodeToString(DoorbellConfig.serializer(), config)
        val decoded = json.decodeFromString(DoorbellConfig.serializer(), encoded)
        assertEquals(config, decoded)
    }

    @Test
    fun `decode list round trip preserves order`() {
        val list =
            listOf(
                DoorbellConfig("a", "A", "binary_sensor.a", "camera.a"),
                DoorbellConfig("b", "B", "binary_sensor.b", "camera.b"),
            )
        val encoded = DoorbellConfig.encodeList(list)
        val decoded = DoorbellConfig.decodeList(encoded)
        assertEquals(list, decoded)
    }

    @Test
    fun `decodeList returns empty on malformed input`() {
        assertEquals(emptyList<DoorbellConfig>(), DoorbellConfig.decodeList(""))
        assertEquals(emptyList<DoorbellConfig>(), DoorbellConfig.decodeList("{not json"))
        assertEquals(emptyList<DoorbellConfig>(), DoorbellConfig.decodeList("null"))
    }

    @Test
    fun `newWith generates a non empty UUID id`() {
        val a = DoorbellConfig.newWith("X", "binary_sensor.x", "camera.x")
        val b = DoorbellConfig.newWith("Y", "binary_sensor.y", "camera.y")
        assertTrue(a.id.isNotEmpty())
        assertTrue(b.id.isNotEmpty())
        assertEquals(true, a.id != b.id)
    }
}

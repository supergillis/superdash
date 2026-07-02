package com.superdash.esphome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the entity-key hash to FNV-1a-32 (with sign bit cleared) over UTF-8 bytes.
 *  Stability across JDK versions matters because HA caches keys in the active
 *  ESPHome session; a silent reshuffle would route commands to the wrong entity. */
class KeyFromObjectIdTest {
    @Test
    fun `light_kitchen has known stable key`() {
        assertEquals(1942446829, keyFromObjectId("light.kitchen"))
    }

    @Test
    fun `keep_screen_on has known stable key`() {
        assertEquals(840007027, keyFromObjectId("keep_screen_on"))
    }

    @Test
    fun `screen_on has known stable key`() {
        assertEquals(450908163, keyFromObjectId("screen_on"))
    }

    @Test
    fun `empty string has known stable key`() {
        assertEquals(18652613, keyFromObjectId(""))
    }

    @Test
    fun `result is always non negative`() {
        val samples = listOf("a", "b", "longer_object_id", "weather.kitchen", "binary_sensor.foo")
        for (objectId in samples) {
            assertTrue("key for $objectId must be non-negative", keyFromObjectId(objectId) >= 0)
        }
    }

    @Test
    fun `distinct object ids produce distinct keys for common names`() {
        val keys = listOf("a", "b", "keep_screen_on", "screen_on", "light.kitchen").map { keyFromObjectId(it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `utf 8 bytes drive the hash`() {
        // Same characters via different code points would change bytes; here we just
        // assert that two different unicode strings produce different keys.
        assertNotEquals(keyFromObjectId("cafe"), keyFromObjectId("café"))
    }
}

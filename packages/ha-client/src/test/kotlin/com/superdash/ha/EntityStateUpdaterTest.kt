package com.superdash.ha

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityStateUpdaterTest {
    private val json = haJson

    private fun event(eventType: String, data: String): HaEvent =
        HaEvent(
            eventType = eventType,
            data = json.parseToJsonElement(data).jsonObject,
            origin = "LOCAL",
            timeFired = "2026-05-06T12:00:00Z",
        )

    @Test fun `state changed with new state adds or updates entity`() {
        val map = emptyMap<String, EntityState>()
        val ev =
            event(
                "state_changed",
                """
            {"entity_id":"light.kitchen","old_state":null,"new_state":{
                "entity_id":"light.kitchen","state":"on",
                "attributes":{"friendly_name":"Kitchen"},
                "last_changed":"2026-05-06T12:00:00Z","last_updated":"2026-05-06T12:00:00Z"
            }}
        """,
            )
        val updated = applyEntityEvent(map, ev)
        assertTrue("light.kitchen" in updated)
        assertEquals("on", updated["light.kitchen"]?.state)
    }

    @Test fun `state changed with null new state removes entity`() {
        val map = mapOf("light.kitchen" to EntityState("light.kitchen", "on"))
        val ev =
            event(
                "state_changed",
                """
            {"entity_id":"light.kitchen","old_state":{"entity_id":"light.kitchen","state":"on","attributes":{}},"new_state":null}
        """,
            )
        val updated = applyEntityEvent(map, ev)
        assertFalse("light.kitchen" in updated)
    }

    @Test fun `unrelated event type does not change map`() {
        val map = mapOf("light.kitchen" to EntityState("light.kitchen", "on"))
        val ev = event("call_service", """{"domain":"light","service":"toggle"}""")
        val updated = applyEntityEvent(map, ev)
        assertEquals(map, updated)
    }
}

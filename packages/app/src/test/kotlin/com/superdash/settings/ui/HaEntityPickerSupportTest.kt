package com.superdash.settings.ui

import com.superdash.ha.EntityState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class HaEntityPickerSupportTest {
    @Test
    fun `filterHaEntities matches friendly name and allowed domains`() {
        val entities =
            listOf(
                entity("binary_sensor.front_door", "off", "Front Door"),
                entity("camera.front", "idle", "Front Camera"),
                entity("weather.home", "sunny", "Home Weather"),
            )

        val filtered =
            filterHaEntities(
                entities = entities,
                query = "front",
                allowedDomains = setOf("camera"),
            )

        assertEquals(listOf("camera.front"), filtered.map { entity -> entity.entityId })
    }

    @Test
    fun `filterHaEntities matches entity id when query has extra whitespace`() {
        val entities =
            listOf(
                entity("sensor.outdoor_temperature", "12", "Outdoor temperature"),
                entity("weather.home", "sunny", "Home Weather"),
            )

        val filtered =
            filterHaEntities(
                entities = entities,
                query = "  outdoor_temperature ",
                allowedDomains = null,
            )

        assertEquals(listOf("sensor.outdoor_temperature"), filtered.map { entity -> entity.entityId })
    }

    private fun entity(
        entityId: String,
        state: String,
        friendlyName: String,
    ): EntityState =
        EntityState(
            entityId = entityId,
            state = state,
            attributes = JsonObject(mapOf("friendly_name" to JsonPrimitive(friendlyName))),
        )
}

package com.superdash.voice

import com.superdash.ha.EntityState
import com.superdash.ha.HaArea
import com.superdash.voice.intent.registry.LocalIntentRegistryCache
import com.superdash.voice.intent.registry.LocalIntentRegistryMetadata
import com.superdash.voice.intent.registry.buildLocalIntentRegistry
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalIntentRegistryCacheTest {
    @Test fun `reuses registry while entity and area maps are unchanged`() {
        val entities = mapOf("light.office" to entity("light.office"))
        val areas = mapOf("office" to HaArea(areaId = "office", name = "Office"))
        var buildCount = 0
        val cache =
            LocalIntentRegistryCache(
                entitiesProvider = { entities },
                areasProvider = { areas },
                buildRegistry = { currentEntities, currentAreas, currentMetadata ->
                    buildCount += 1
                    buildLocalIntentRegistry(currentEntities, currentAreas, currentMetadata)
                },
            )

        cache.current()
        cache.current()

        assertEquals(1, buildCount)
    }

    @Test fun `rebuilds registry when entity map changes`() {
        var entities = mapOf("light.office" to entity("light.office"))
        val areas = emptyMap<String, HaArea>()
        var buildCount = 0
        val cache =
            LocalIntentRegistryCache(
                entitiesProvider = { entities },
                areasProvider = { areas },
                buildRegistry = { currentEntities, currentAreas, currentMetadata ->
                    buildCount += 1
                    buildLocalIntentRegistry(currentEntities, currentAreas, currentMetadata)
                },
            )

        cache.current()
        entities = mapOf("light.desk" to entity("light.desk"))
        cache.current()

        assertEquals(2, buildCount)
    }

    @Test fun `returns unavailable snapshot until voice metadata is loaded`() {
        val cache =
            LocalIntentRegistryCache(
                entitiesProvider = { mapOf("light.office" to entity("light.office")) },
                areasProvider = { emptyMap() },
                metadataProvider = { LocalIntentRegistryMetadata.unavailable() },
            )

        assertEquals(false, cache.current().available)
    }

    private fun entity(entityId: String): EntityState =
        EntityState(
            entityId = entityId,
            state = "off",
            attributes = JsonObject(emptyMap()),
        )
}

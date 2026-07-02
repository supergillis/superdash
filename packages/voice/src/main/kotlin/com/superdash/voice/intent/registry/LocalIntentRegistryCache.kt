package com.superdash.voice.intent.registry

import com.superdash.ha.EntityState
import com.superdash.ha.HaArea

private typealias LocalIntentRegistryFactory =
    (
        Map<String, EntityState>,
        Map<String, HaArea>,
        LocalIntentRegistryMetadata,
    ) -> LocalIntentRegistry

class LocalIntentRegistryCache(
    private val entitiesProvider: () -> Map<String, EntityState>,
    private val areasProvider: () -> Map<String, HaArea>,
    private val metadataProvider: () -> LocalIntentRegistryMetadata = { LocalIntentRegistryMetadata.unavailable() },
    private val buildRegistry: LocalIntentRegistryFactory =
        { entities, areas, metadata ->
            buildLocalIntentRegistry(
                entities = entities,
                areas = areas,
                metadata = metadata,
            )
        },
) {
    private var cachedEntities: Map<String, EntityState>? = null
    private var cachedAreas: Map<String, HaArea>? = null
    private var cachedMetadata: LocalIntentRegistryMetadata? = null
    private var cachedSnapshot: LocalIntentRegistrySnapshot? = null

    fun current(): LocalIntentRegistrySnapshot {
        val entities = entitiesProvider()
        val areas = areasProvider()
        val metadata = metadataProvider()
        return synchronized(this) {
            val snapshot = cachedSnapshot
            val canReuseSnapshot =
                snapshot != null &&
                    cachedEntities === entities &&
                    cachedAreas === areas &&
                    cachedMetadata == metadata
            if (canReuseSnapshot) {
                snapshot
            } else {
                LocalIntentRegistrySnapshot(
                    registry = buildRegistry(entities, areas, metadata),
                    available = metadata.loaded,
                ).also { newSnapshot ->
                    cachedEntities = entities
                    cachedAreas = areas
                    cachedMetadata = metadata
                    cachedSnapshot = newSnapshot
                }
            }
        }
    }
}

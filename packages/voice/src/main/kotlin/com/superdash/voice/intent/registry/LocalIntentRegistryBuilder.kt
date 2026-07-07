package com.superdash.voice.intent.registry

import com.superdash.ha.EntityState
import com.superdash.ha.HaArea
import com.superdash.voice.intent.LocalIntentDefinition
import com.superdash.voice.intent.defaultLocalIntentCatalog

fun buildLocalIntentRegistry(
    entities: Map<String, EntityState>,
    areas: Map<String, HaArea> = emptyMap(),
    metadata: LocalIntentRegistryMetadata = LocalIntentRegistryMetadata.unavailable(),
    intentDefinitions: List<LocalIntentDefinition> = defaultLocalIntentCatalog(),
): LocalIntentRegistry {
    if (!metadata.loaded) {
        return LocalIntentRegistry(emptyList())
    }
    val exposedEntities =
        entities
            .filterKeys { entityId -> entityId in metadata.exposedEntityIds }
            // Local direct execution currently knows how to safely map only light and switch voice commands.
            .filterKeys { entityId -> entityId.substringBefore(".") in setOf("light", "switch") }
    val commands =
        areaIntentCommands(exposedEntities, areas, metadata, intentDefinitions) +
            entityIntentCommands(exposedEntities, metadata, intentDefinitions)
    return LocalIntentRegistry(commands)
}

private fun areaIntentCommands(
    entities: Map<String, EntityState>,
    areas: Map<String, HaArea>,
    metadata: LocalIntentRegistryMetadata,
    intentDefinitions: List<LocalIntentDefinition>,
): List<LocalGeneratedIntentCommand> =
    areas.values.flatMap { area ->
        val areaEntities =
            entities
                .values
                .filter { entity -> localIntentAreaId(entity, metadata) == area.areaId }
                .sortedBy { entity -> entity.entityId }
        if (areaEntities.isEmpty()) {
            return@flatMap emptyList()
        }
        val areaNames = localIntentAreaNames(area)
        val lightEntityIds =
            areaEntities
                .filter { entity -> entity.entityId.startsWith("light.") }
                .map { entity -> entity.entityId }
        val genericTarget =
            LocalIntentTarget(
                id = area.areaId,
                kind = LocalGeneratedIntentTargetKind.Area,
                names = areaNames,
                entityIds = areaEntities.map { entity -> entity.entityId },
            )
        val lightTarget = genericTarget.copy(entityIds = lightEntityIds)
        areaGenericCommands(genericTarget, intentDefinitions) +
            if (lightEntityIds.isNotEmpty()) {
                areaLightCommands(lightTarget, intentDefinitions)
            } else {
                emptyList()
            }
    }

private fun entityIntentCommands(
    entities: Map<String, EntityState>,
    metadata: LocalIntentRegistryMetadata,
    intentDefinitions: List<LocalIntentDefinition>,
): List<LocalGeneratedIntentCommand> =
    entities.values.flatMap { entity ->
        val domain = entity.entityId.substringBefore(".")
        localIntentEntityNames(entity, metadata).flatMap { entityName ->
            val target =
                LocalIntentTarget(
                    id = entity.entityId,
                    kind = LocalGeneratedIntentTargetKind.Entity,
                    names = listOf(entityName),
                )
            entityCommands(target, domain, entityName, intentDefinitions)
        }
    }

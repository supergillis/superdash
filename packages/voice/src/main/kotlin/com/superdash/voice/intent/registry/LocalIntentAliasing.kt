package com.superdash.voice.intent.registry

import com.superdash.ha.EntityState
import com.superdash.ha.HaArea
import com.superdash.voice.intent.normalizeLocalIntentPhrase
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun localIntentAreaNames(area: HaArea): List<String> =
    (listOf(area.name) + area.aliases)
        .map { name -> normalizeLocalIntentPhrase(name) }
        .filter { name -> name.isNotBlank() }
        .distinct()

internal fun localIntentAreaId(
    entity: EntityState,
    metadata: LocalIntentRegistryMetadata,
): String? {
    val registryEntry = metadata.entityRegistry[entity.entityId]
    val deviceAreaId =
        registryEntry
            ?.deviceId
            ?.let { deviceId -> metadata.deviceRegistry[deviceId]?.areaId }
    return registryEntry?.areaId ?: deviceAreaId
}

internal fun localIntentEntityNames(
    entity: EntityState,
    metadata: LocalIntentRegistryMetadata,
): List<String> {
    val registryEntry = metadata.entityRegistry[entity.entityId]
    val friendlyName =
        entity
            .attributes["friendly_name"]
            ?.jsonPrimitive
            ?.contentOrNull
    val entityObjectName = entity.entityId.substringAfter(".").replace("_", " ")
    val names =
        listOfNotNull(friendlyName, registryEntry?.name, registryEntry?.originalName, entityObjectName) +
            registryEntry?.aliases.orEmpty()
    val aliases = names.flatMap { name -> localIntentEntityAliases(entity, name) }
    return aliases.distinct()
}

private fun localIntentEntityAliases(
    entity: EntityState,
    name: String,
): List<String> {
    val normalizedName = normalizeLocalIntentPhrase(name)
    if (normalizedName.isBlank()) {
        return emptyList()
    }
    val aliases = mutableListOf(normalizedName)
    val alreadyNamesLight =
        normalizedName.endsWith(" light") ||
            normalizedName.endsWith(" lights")
    if (entity.entityId.startsWith("light.") && !alreadyNamesLight) {
        aliases += "$normalizedName lights"
    }
    return aliases
}

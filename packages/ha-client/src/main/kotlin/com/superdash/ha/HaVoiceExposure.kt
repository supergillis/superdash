package com.superdash.ha

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

fun extractConversationExposure(result: JsonObject): HaVoiceExposureSnapshot {
    val exposedEntities = result["exposed_entities"] as? JsonObject ?: return unloadedExposure()
    val officialShape = extractOfficialConversationExposure(exposedEntities)
    if (officialShape != null) {
        return officialShape
    }

    val conversation = exposedEntities["conversation"] as? JsonObject ?: return unloadedExposure()
    val exposedIds =
        conversation
            .filterValues { value -> isExposedConversationValue(value) }
            .keys
            .toSet()
    return HaVoiceExposureSnapshot(exposedEntityIds = exposedIds, loaded = true)
}

fun extractConversationExposureFromEntityRegistry(
    entries: Iterable<HaEntityRegistryEntry>,
): HaVoiceExposureSnapshot {
    val registryEntries = entries.toList()
    if (registryEntries.isEmpty()) {
        return HaVoiceExposureSnapshot(exposedEntityIds = emptySet(), loaded = false)
    }
    val exposedIds =
        registryEntries
            .filter { entry -> entry.options.conversation?.shouldExpose == true }
            .map { entry -> entry.entityId }
            .toSet()
    return HaVoiceExposureSnapshot(exposedEntityIds = exposedIds, loaded = true)
}

private fun extractOfficialConversationExposure(exposedEntities: JsonObject): HaVoiceExposureSnapshot? {
    if (exposedEntities["conversation"] is JsonObject) {
        return null
    }
    val exposedIds =
        exposedEntities
            .filterValues { value ->
                val assistants = value as? JsonObject
                assistants?.get("conversation")?.jsonPrimitive?.booleanOrNull == true
            }.keys
            .toSet()
    return HaVoiceExposureSnapshot(exposedEntityIds = exposedIds, loaded = true)
}

private fun isExposedConversationValue(value: JsonElement): Boolean {
    val primitive = value as? JsonPrimitive
    if (primitive != null) {
        return primitive.booleanOrNull != false
    }

    val jsonObject = value as? JsonObject ?: return true
    val exposed = jsonObject["exposed"]?.jsonPrimitive?.booleanOrNull
    if (exposed == false) {
        return false
    }
    val conversation = jsonObject["conversation"]?.jsonPrimitive?.booleanOrNull
    if (conversation == false) {
        return false
    }
    return true
}

private fun unloadedExposure(): HaVoiceExposureSnapshot =
    HaVoiceExposureSnapshot(exposedEntityIds = emptySet(), loaded = false)

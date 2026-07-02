package com.superdash.voice.intent.registry

import com.superdash.ha.HaDeviceRegistryEntry
import com.superdash.ha.HaEntityRegistryEntry

data class LocalIntentRegistryMetadata(
    val entityRegistry: Map<String, HaEntityRegistryEntry>,
    val deviceRegistry: Map<String, HaDeviceRegistryEntry>,
    val exposedEntityIds: Set<String>,
    val loaded: Boolean,
) {
    companion object {
        fun unavailable(): LocalIntentRegistryMetadata =
            LocalIntentRegistryMetadata(
                entityRegistry = emptyMap(),
                deviceRegistry = emptyMap(),
                exposedEntityIds = emptySet(),
                loaded = false,
            )
    }
}

data class LocalIntentRegistrySnapshot(
    val registry: LocalIntentRegistry,
    val available: Boolean,
)

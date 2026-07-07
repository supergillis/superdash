package com.superdash.doorbell

import com.superdash.core.json.coreJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

@Serializable
data class DoorbellConfig(
    val id: String,
    val name: String,
    val triggerEntity: String,
    /** Either an HA camera entity id (resolved via `camera/stream`) or a
     *  direct stream URL (passed straight to ExoPlayer). [parseCameraSource]
     *  decides which at playback time. */
    val cameraEntity: String,
) {
    companion object {
        private val listSerializer = ListSerializer(serializer())

        fun newWith(name: String, triggerEntity: String, cameraEntity: String): DoorbellConfig =
            DoorbellConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                triggerEntity = triggerEntity,
                cameraEntity = cameraEntity,
            )

        fun encodeList(list: List<DoorbellConfig>): String =
            coreJson.encodeToString(listSerializer, list)

        fun decodeList(jsonText: String): List<DoorbellConfig> =
            runCatching { coreJson.decodeFromString(listSerializer, jsonText) }
                .getOrDefault(emptyList())
    }
}

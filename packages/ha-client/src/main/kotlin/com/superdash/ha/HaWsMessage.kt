package com.superdash.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Shared Json configuration for all HA wire serialization.
 *  - classDiscriminator = "type" matches HA's WS frame envelope.
 *  - ignoreUnknownKeys: HA adds fields over time; ignore them.
 *  - encodeDefaults = false: don't include default-valued fields on the wire,
 *    matching the prior buildJsonObject usage. */
val haJson: Json =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

/** Inbound HA WebSocket frames. Discriminated by "type". */
@Serializable
sealed interface HaWsFrame

@Serializable
@SerialName("auth_required")
data class AuthRequired(
    @SerialName("ha_version") val haVersion: String,
) : HaWsFrame

@Serializable
@SerialName("auth_ok")
data class AuthOk(
    @SerialName("ha_version") val haVersion: String,
) : HaWsFrame

@Serializable
@SerialName("auth_invalid")
data class AuthInvalid(
    val message: String,
) : HaWsFrame

@Serializable
@SerialName("result")
data class ResultFrame(
    val id: Int,
    val success: Boolean,
    val result: JsonElement? = null,
    val error: ResultError? = null,
) : HaWsFrame

@Serializable
@SerialName("event")
data class EventFrame(
    val id: Int,
    val event: HaEvent,
) : HaWsFrame

@Serializable
@SerialName("pong")
data class Pong(
    val id: Int,
) : HaWsFrame

@Serializable
data class StateChangedEventData(
    @SerialName("entity_id") val entityId: String,
    @SerialName("new_state") val newState: EntityState? = null,
)

@Serializable
data class CameraStreamResult(
    val url: String? = null,
)

@Serializable
data class HaEvent(
    @SerialName("event_type") val eventType: String,
    val data: JsonObject,
    val origin: String,
    @SerialName("time_fired") val timeFired: String,
)

@Serializable
data class ResultError(
    val code: String,
    val message: String,
)

internal inline fun <reified T> ResultFrame.requireResultList(commandName: String): List<T> {
    if (!success) {
        error("$commandName failed: $error")
    }
    val payload = result ?: error("expected result array for $commandName")
    return haJson.decodeFromJsonElement(payload)
}

/** Decodes a [JsonObject] response into a [ResultFrame], asserting success.
 *  Throws [HaResultException] when the frame reports failure so callers can
 *  translate to a typed exception without re-checking [ResultFrame.success]. */
internal fun JsonObject.requireResult(commandName: String): ResultFrame {
    val frame = haJson.decodeFromJsonElement(ResultFrame.serializer(), this)
    if (!frame.success) {
        val code = frame.error?.code ?: "unknown"
        val haMessage = frame.error?.message ?: "$commandName failed"
        throw HaResultException(commandName = commandName, code = code, haMessage = haMessage)
    }
    return frame
}

class HaResultException(
    val commandName: String,
    val code: String,
    /** The raw `error.message` from the HA result frame, without command/code prefix. */
    val haMessage: String,
) : Exception("$commandName: $code: $haMessage")

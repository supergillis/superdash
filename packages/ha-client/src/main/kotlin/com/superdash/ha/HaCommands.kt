package com.superdash.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Outbound commands. */
@Serializable
sealed interface HaCommand

@Serializable
@SerialName("auth")
data class AuthCommand(
    @SerialName("access_token") val accessToken: String,
) : HaCommand

@Serializable
@SerialName("get_states")
data class GetStatesCommand(
    val id: Int,
) : HaCommand

@Serializable
@SerialName("config/area_registry/list")
data class ListAreasCommand(
    val id: Int,
) : HaCommand

@Serializable
@SerialName("config/entity_registry/list")
data class ListEntityRegistryCommand(
    val id: Int,
) : HaCommand

@Serializable
@SerialName("config/device_registry/list")
data class ListDeviceRegistryCommand(
    val id: Int,
) : HaCommand

@Serializable
@SerialName("homeassistant/expose_entity/list")
data class ListVoiceExposedEntitiesCommand(
    val id: Int,
) : HaCommand

@Serializable
@SerialName("subscribe_events")
data class SubscribeEventsCommand(
    val id: Int,
    @SerialName("event_type") val eventType: String,
) : HaCommand

@Serializable
@SerialName("ping")
data class PingCommand(
    val id: Int,
) : HaCommand

@Serializable
@SerialName("call_service")
data class CallServiceCommand(
    val id: Int,
    val domain: String,
    val service: String,
    @SerialName("service_data") val serviceData: JsonObject? = null,
    val target: HaServiceTarget? = null,
    @SerialName("return_response") val returnResponse: Boolean? = null,
) : HaCommand

@Serializable
data class HaServiceTarget(
    @SerialName("entity_id") val entityId: List<String>? = null,
    @SerialName("device_id") val deviceId: List<String>? = null,
    @SerialName("area_id") val areaId: List<String>? = null,
)

@Serializable
data class HaArea(
    @SerialName("area_id") val areaId: String,
    val name: String,
    val aliases: List<String> = emptyList(),
)

@Serializable
data class HaEntityRegistryEntry(
    @SerialName("entity_id") val entityId: String,
    @SerialName("area_id") val areaId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    val aliases: List<String> = emptyList(),
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("disabled_by") val disabledBy: String? = null,
    @SerialName("hidden_by") val hiddenBy: String? = null,
    val options: HaEntityRegistryOptions = HaEntityRegistryOptions(),
)

@Serializable
data class HaDeviceRegistryEntry(
    @SerialName("id") val deviceId: String,
    @SerialName("area_id") val areaId: String? = null,
)

@Serializable
data class HaEntityRegistryOptions(
    val conversation: HaConversationEntityOptions? = null,
)

@Serializable
data class HaConversationEntityOptions(
    @SerialName("should_expose") val shouldExpose: Boolean? = null,
)

data class HaServiceCall(
    val domain: String,
    val service: String,
    val serviceData: JsonObject? = null,
    val target: HaServiceTarget? = null,
    val returnResponse: Boolean? = null,
)

data class HaServiceCallResult(
    val success: Boolean,
    val result: JsonElement? = null,
)

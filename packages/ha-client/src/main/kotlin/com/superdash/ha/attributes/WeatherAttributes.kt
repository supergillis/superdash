package com.superdash.ha.attributes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire mirror of HA `weather.*` entity attributes. Owned by ha-client so
 *  feature packages keep only domain types. */
@Serializable
data class WeatherAttributes(
    val temperature: Double? = null,
    @SerialName("temperature_unit") val temperatureUnit: String? = null,
    val humidity: Double? = null,
    val forecast: List<ForecastAttributes>? = null,
)

@Serializable
data class ForecastAttributes(
    val datetime: String? = null,
    val condition: String? = null,
    val temperature: Double? = null,
    @SerialName("templow") val tempLow: Double? = null,
)

package com.superdash.screensaver

import com.superdash.ha.EntityState
import com.superdash.ha.attributes.WeatherAttributes
import com.superdash.ha.haJson
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.decodeFromJsonElement

/** Pure parser of HA weather entity → typed snapshot.
 *
 *  Caps the forecast list to the first 3 entries because the HUD only
 *  renders that many. */
data class WeatherSnapshot(
    val state: String,
    val temperatureC: Double?,
    val unit: String?,
    val humidity: Double?,
    val forecast: ImmutableList<ForecastDay>,
) {
    data class ForecastDay(
        val date: String,
        val condition: String,
        val tempHi: Double?,
        val tempLo: Double?,
    )

    companion object {
        fun fromEntity(entity: EntityState?): WeatherSnapshot? {
            if (entity == null) {
                return null
            }
            val attributes =
                runCatching {
                    haJson.decodeFromJsonElement<WeatherAttributes>(entity.attributes)
                }.getOrNull() ?: WeatherAttributes()
            val forecast: ImmutableList<ForecastDay> =
                attributes.forecast
                    ?.take(3)
                    ?.map { day ->
                        ForecastDay(
                            date = day.datetime.orEmpty(),
                            condition = day.condition ?: "unknown",
                            tempHi = day.temperature,
                            tempLo = day.tempLow,
                        )
                    }?.toImmutableList() ?: persistentListOf()
            return WeatherSnapshot(
                state = entity.state,
                temperatureC = attributes.temperature,
                unit = attributes.temperatureUnit,
                humidity = attributes.humidity,
                forecast = forecast,
            )
        }
    }
}

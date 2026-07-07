package com.superdash.ha.attributes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire mirror of HA `calendar.*` entity attributes. When the entity is
 *  `on`, these describe the in-progress event; when `off`, they describe
 *  the next upcoming event. */
@Serializable
data class CalendarEventAttributes(
    val message: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
)

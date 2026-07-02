package com.superdash.screensaver

import com.superdash.ha.EntityState
import com.superdash.ha.attributes.CalendarEventAttributes
import com.superdash.ha.haJson
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.decodeFromJsonElement

/** Pure parser of HA `calendar.*` entity → typed snapshot of the next event.
 *
 *  HA exposes one event at a time. `state == "on"` means the event is in
 *  progress; `state == "off"` means the attributes describe the next
 *  upcoming event. Returns null when the entity is missing, attributes are
 *  unparseable, the message is blank, or the event already ended. */
data class CalendarSnapshot(
    val message: String,
    val startTime: Instant?,
    val endTime: Instant?,
    val allDay: Boolean,
    val inProgress: Boolean,
) {
    companion object {
        fun fromEntity(
            entity: EntityState?,
            now: Instant = Clock.System.now(),
            zone: TimeZone = TimeZone.currentSystemDefault(),
        ): CalendarSnapshot? {
            if (entity == null) {
                return null
            }
            val attributes =
                runCatching {
                    haJson.decodeFromJsonElement<CalendarEventAttributes>(entity.attributes)
                }.getOrNull() ?: return null
            val message = attributes.message?.trim().orEmpty()
            if (message.isEmpty()) {
                return null
            }
            val start = parseHaTime(attributes.startTime, zone)
            val end = parseHaTime(attributes.endTime, zone)
            if (end != null && end <= now) {
                return null
            }
            return CalendarSnapshot(
                message = message,
                startTime = start,
                endTime = end,
                allDay = attributes.allDay,
                inProgress = entity.state == "on",
            )
        }

        /** HA emits either ISO-8601 with offset (`2026-05-18T14:00:00+02:00`),
         *  a space-separated local datetime (`2026-05-18 14:00:00`), or a
         *  date-only string (`2026-05-18`) for all-day events. */
        private fun parseHaTime(
            raw: String?,
            zone: TimeZone,
        ): Instant? {
            if (raw.isNullOrBlank()) {
                return null
            }
            val trimmed = raw.trim()
            runCatching {
                return Instant.parse(trimmed)
            }
            runCatching {
                return LocalDateTime.parse(trimmed.replace(' ', 'T')).toInstant(zone)
            }
            runCatching {
                return LocalDate.parse(trimmed).atStartOfDayIn(zone)
            }
            return null
        }
    }
}

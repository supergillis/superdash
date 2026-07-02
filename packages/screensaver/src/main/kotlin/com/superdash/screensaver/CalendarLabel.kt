package com.superdash.screensaver

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_MESSAGE_CHARS = 31
private const val SEP = " · "

/** Format the event prefix for the ambient overlay's event line. */
fun formatCalendarLabel(
    snapshot: CalendarSnapshot,
    now: Instant,
    zone: TimeZone,
    locale: Locale = Locale.getDefault(),
): String {
    val message = truncate(snapshot.message)
    val prefix = prefixFor(snapshot, now, zone, locale) ?: return message
    return "$prefix$SEP$message"
}

private fun prefixFor(
    snapshot: CalendarSnapshot,
    now: Instant,
    zone: TimeZone,
    locale: Locale,
): String? {
    if (snapshot.inProgress) {
        return "Now"
    }
    val start = snapshot.startTime ?: return null
    val nowLocal = now.toLocalDateTime(zone)
    val startLocal = start.toLocalDateTime(zone)
    val dayDelta = startLocal.date.toEpochDays() - nowLocal.date.toEpochDays()
    val time = "%02d:%02d".format(startLocal.hour, startLocal.minute)
    val javaStart = startLocal.toJavaLocalDateTime()
    val dayName = DateTimeFormatter.ofPattern("EEE", locale).format(javaStart)
    val monthName = DateTimeFormatter.ofPattern("MMM", locale).format(javaStart)
    return when {
        snapshot.allDay && dayDelta == 0 -> "Today"
        snapshot.allDay && dayDelta == 1 -> "Tomorrow"
        snapshot.allDay && dayDelta in 2..6 -> dayName
        snapshot.allDay -> "${startLocal.dayOfMonth} $monthName"
        dayDelta == 0 -> time
        dayDelta == 1 -> "Tomorrow $time"
        dayDelta in 2..6 -> "$dayName $time"
        else -> "${startLocal.dayOfMonth} $monthName $time"
    }
}

private fun truncate(message: String): String =
    if (message.length <= MAX_MESSAGE_CHARS) {
        message
    } else {
        message.take(MAX_MESSAGE_CHARS) + "…"
    }

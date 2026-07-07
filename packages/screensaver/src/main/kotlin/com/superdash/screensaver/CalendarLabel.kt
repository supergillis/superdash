package com.superdash.screensaver

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_MESSAGE_CHARS = 31
private const val SEP = " · "

/** Format the event prefix for the ambient overlay's event line.
 *
 *  Pure and testable: localized label strings and the pre-resolved time
 *  pattern are passed in rather than resolved here, so no
 *  `stringResource`/context lookups happen inside this function. */
fun formatCalendarLabel(
    snapshot: CalendarSnapshot,
    now: Instant,
    zone: TimeZone,
    locale: Locale = Locale.getDefault(),
    nowLabel: String = "Now",
    todayLabel: String = "Today",
    tomorrowLabel: String = "Tomorrow",
    timePattern: String = "HH:mm",
): String {
    val message = truncate(snapshot.message)
    val prefix =
        prefixFor(snapshot, now, zone, locale, nowLabel, todayLabel, tomorrowLabel, timePattern)
            ?: return message
    return "$prefix$SEP$message"
}

private fun prefixFor(
    snapshot: CalendarSnapshot,
    now: Instant,
    zone: TimeZone,
    locale: Locale,
    nowLabel: String,
    todayLabel: String,
    tomorrowLabel: String,
    timePattern: String,
): String? {
    if (snapshot.inProgress) {
        return nowLabel
    }
    val start = snapshot.startTime ?: return null
    val nowLocal = now.toLocalDateTime(zone)
    val startLocal = start.toLocalDateTime(zone)
    val dayDelta = startLocal.date.toEpochDays() - nowLocal.date.toEpochDays()
    val javaStart = startLocal.toJavaLocalDateTime()
    // [timePattern] is resolved by the caller from the pre-resolved time pattern string
    // (e.g. via the Android `timePattern()`/`DateFormat.getBestDateTimePattern` helper in
    // composable scope), which this pure/JVM-testable function must not depend on directly.
    // `DateTimeFormatter` here is pure java.time and localizes separators and the AM/PM
    // marker text for `locale`.
    val time = DateTimeFormatter.ofPattern(timePattern, locale).format(javaStart)
    // EEE/MMM skeletons are already locale-correct abbreviations (no numeric ordering to fix),
    // so they're left as-is rather than routed through `localizedDatePattern`.
    val dayName = DateTimeFormatter.ofPattern("EEE", locale).format(javaStart)
    val monthName = DateTimeFormatter.ofPattern("MMM", locale).format(javaStart)
    return when {
        snapshot.allDay && dayDelta == 0 -> todayLabel
        snapshot.allDay && dayDelta == 1 -> tomorrowLabel
        snapshot.allDay && dayDelta in 2..6 -> dayName
        snapshot.allDay -> "${startLocal.dayOfMonth} $monthName"
        dayDelta == 0 -> time
        dayDelta == 1 -> "$tomorrowLabel $time"
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

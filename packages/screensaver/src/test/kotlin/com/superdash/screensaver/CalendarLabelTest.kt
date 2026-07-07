package com.superdash.screensaver

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CalendarLabelTest {
    private val zone = TimeZone.of("UTC")
    private val now = Instant.parse("2026-05-18T10:00:00Z") // Monday

    private fun snap(
        message: String = "Dentist",
        start: Instant? = null,
        end: Instant? = null,
        allDay: Boolean = false,
        inProgress: Boolean = false,
    ) = CalendarSnapshot(message, start, end, allDay, inProgress)

    @Test fun `in progress shows Now`() {
        val label =
            formatCalendarLabel(
                snap(
                    start = Instant.parse("2026-05-18T09:30:00Z"),
                    end = Instant.parse("2026-05-18T10:30:00Z"),
                    inProgress = true,
                ),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("Now · Dentist", label)
    }

    @Test fun `timed today shows HH-mm`() {
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-05-18T14:00:00Z")),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("14:00 · Dentist", label)
    }

    @Test fun `timed today with 12-hour pattern shows AM-PM`() {
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-05-18T14:00:00Z")),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "h:mm a",
            )
        assertEquals("2:00 PM · Dentist", label)
    }

    @Test fun `timed tomorrow shows Tomorrow HH-mm`() {
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-05-19T08:00:00Z")),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("Tomorrow 08:00 · Dentist", label)
    }

    @Test fun `all-day today shows Today`() {
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-05-18T00:00:00Z"), allDay = true),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("Today · Dentist", label)
    }

    @Test fun `all-day tomorrow shows Tomorrow`() {
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-05-19T00:00:00Z"), allDay = true),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("Tomorrow · Dentist", label)
    }

    @Test fun `timed within 7 days shows EEE HH-mm`() {
        // 2026-05-21 is Thursday
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-05-21T13:00:00Z")),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("Thu 13:00 · Dentist", label)
    }

    @Test fun `all-day within 7 days shows EEE`() {
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-05-22T00:00:00Z"), allDay = true),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("Fri · Dentist", label)
    }

    @Test fun `timed beyond 7 days shows d MMM HH-mm`() {
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-06-02T09:00:00Z")),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("2 Jun 09:00 · Dentist", label)
    }

    @Test fun `all-day beyond 7 days shows d MMM`() {
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-06-02T00:00:00Z"), allDay = true),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("2 Jun · Dentist", label)
    }

    @Test fun `exactly 7 days out shows d MMM`() {
        // 2026-05-25 = 7 days after 2026-05-18 (Monday)
        val label =
            formatCalendarLabel(
                snap(start = Instant.parse("2026-05-25T09:00:00Z")),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("25 May 09:00 · Dentist", label)
    }

    @Test fun `long message is truncated at 32 chars`() {
        val long = "A very long event title that runs on and on"
        val label =
            formatCalendarLabel(
                snap(
                    message = long,
                    start = Instant.parse("2026-05-18T14:00:00Z"),
                ),
                now,
                zone,
                Locale.ENGLISH,
                nowLabel = "Now",
                todayLabel = "Today",
                tomorrowLabel = "Tomorrow",
                timePattern = "HH:mm",
            )
        assertEquals("14:00 · A very long event title that ru…", label)
    }

    @Test fun `missing start falls back to message only`() {
        val label = formatCalendarLabel(snap(start = null), now, zone, Locale.ENGLISH)
        assertEquals("Dentist", label)
    }
}

package com.superdash.screensaver

import com.superdash.core.json.coreJson
import com.superdash.ha.EntityState
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class CalendarSnapshotTest {
    private val json = coreJson
    private val now = Instant.parse("2026-05-18T10:00:00Z")
    private val zone = TimeZone.UTC

    private fun entity(payload: String): EntityState =
        json.decodeFromString(EntityState.serializer(), payload)

    @Test fun `null entity returns null`() {
        assertNull(CalendarSnapshot.fromEntity(null, now, zone))
    }

    @Test fun `parses upcoming event`() {
        val entity =
            entity(
                """
                {"entity_id":"calendar.home","state":"off",
                 "attributes":{"message":"Dentist",
                   "start_time":"2026-05-18 14:00:00",
                   "end_time":"2026-05-18 15:00:00","all_day":false}}
                """.trimIndent(),
            )
        val snap = CalendarSnapshot.fromEntity(entity, now, zone)
        assertNotNull(snap)
        assertEquals("Dentist", snap!!.message)
        assertEquals(Instant.parse("2026-05-18T14:00:00Z"), snap.startTime)
        assertEquals(Instant.parse("2026-05-18T15:00:00Z"), snap.endTime)
        assertFalse(snap.allDay)
        assertFalse(snap.inProgress)
    }

    @Test fun `state on marks in progress`() {
        val entity =
            entity(
                """
                {"entity_id":"calendar.home","state":"on",
                 "attributes":{"message":"Meeting",
                   "start_time":"2026-05-18 09:30:00",
                   "end_time":"2026-05-18 10:30:00","all_day":false}}
                """.trimIndent(),
            )
        val snap = CalendarSnapshot.fromEntity(entity, now, zone)!!
        assertTrue(snap.inProgress)
    }

    @Test fun `parses all day event with date-only fields`() {
        val entity =
            entity(
                """
                {"entity_id":"calendar.home","state":"off",
                 "attributes":{"message":"Mom's birthday",
                   "start_time":"2026-05-19","end_time":"2026-05-20","all_day":true}}
                """.trimIndent(),
            )
        val snap = CalendarSnapshot.fromEntity(entity, now, zone)!!
        assertTrue(snap.allDay)
        assertNotNull(snap.startTime)
        assertNotNull(snap.endTime)
    }

    @Test fun `past event returns null`() {
        val entity =
            entity(
                """
                {"entity_id":"calendar.home","state":"off",
                 "attributes":{"message":"Old",
                   "start_time":"2026-05-17 09:00:00",
                   "end_time":"2026-05-17 10:00:00","all_day":false}}
                """.trimIndent(),
            )
        assertNull(CalendarSnapshot.fromEntity(entity, now, zone))
    }

    @Test fun `blank message returns null`() {
        val entity =
            entity(
                """
                {"entity_id":"calendar.home","state":"off",
                 "attributes":{"message":"",
                   "start_time":"2026-05-18 14:00:00",
                   "end_time":"2026-05-18 15:00:00","all_day":false}}
                """.trimIndent(),
            )
        assertNull(CalendarSnapshot.fromEntity(entity, now, zone))
    }

    @Test fun `parses ISO-8601 with offset`() {
        val entity =
            entity(
                """
                {"entity_id":"calendar.home","state":"off",
                 "attributes":{"message":"Meeting",
                   "start_time":"2026-05-18T14:00:00+02:00",
                   "end_time":"2026-05-18T15:00:00+02:00","all_day":false}}
                """.trimIndent(),
            )
        val snap = CalendarSnapshot.fromEntity(entity, now, TimeZone.UTC)!!
        assertEquals(Instant.parse("2026-05-18T12:00:00Z"), snap.startTime)
        assertEquals(Instant.parse("2026-05-18T13:00:00Z"), snap.endTime)
    }

    @Test fun `missing attributes return null`() {
        val entity =
            entity(
                """
                {"entity_id":"calendar.home","state":"off","attributes":{}}
                """.trimIndent(),
            )
        assertNull(CalendarSnapshot.fromEntity(entity, now, zone))
    }

    @Test fun `selects the soonest event across calendars`() {
        fun snapshot(
            message: String,
            start: String?,
            inProgress: Boolean = false,
        ) = CalendarSnapshot(
            message = message,
            startTime = start?.let { Instant.parse(it) },
            endTime = null,
            allDay = false,
            inProgress = inProgress,
        )

        val later = snapshot("later", "2026-05-18T15:00:00Z")
        val sooner = snapshot("sooner", "2026-05-18T12:00:00Z")
        val running = snapshot("running", "2026-05-18T09:00:00Z", inProgress = true)
        val runningEarlier = snapshot("running earlier", "2026-05-18T08:00:00Z", inProgress = true)
        val undated = snapshot("undated", null)
        val cases =
            listOf(
                emptyList<CalendarSnapshot>() to null,
                listOf(later) to later,
                listOf(later, sooner) to sooner,
                listOf(sooner, running) to running,
                listOf(running, runningEarlier) to runningEarlier,
                listOf(undated, later) to later,
                listOf(sooner, snapshot("same start", "2026-05-18T12:00:00Z")) to sooner,
            )
        cases.forEach { (snapshots, expected) ->
            assertEquals(expected, selectSoonestCalendarEvent(snapshots))
        }
    }
}

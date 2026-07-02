package com.superdash.screensaver

import com.superdash.core.json.coreJson
import com.superdash.ha.EntityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherSnapshotTest {
    private val json = coreJson

    private fun entity(payload: String): EntityState = json.decodeFromString(EntityState.serializer(), payload)

    @Test fun `null entity returns null`() {
        assertNull(WeatherSnapshot.fromEntity(null))
    }

    @Test fun `parses sunny with temperature`() {
        val entity =
            entity(
                """
                {"entity_id":"weather.forecast_home","state":"sunny",
                 "attributes":{"temperature":21.4,"temperature_unit":"°C","humidity":55,"forecast":[]}}
                """.trimIndent(),
            )
        val snap = WeatherSnapshot.fromEntity(entity)
        assertNotNull(snap)
        assertEquals("sunny", snap!!.state)
        assertEquals(21.4, snap.temperatureC!!, 0.001)
        assertEquals("°C", snap.unit)
        assertEquals(55.0, snap.humidity!!, 0.001)
        assertTrue(snap.forecast.isEmpty())
    }

    @Test fun `parses three forecast days`() {
        val entity =
            entity(
                """
                {"entity_id":"weather.forecast_home","state":"partlycloudy",
                 "attributes":{"temperature":15,"temperature_unit":"°C",
                   "forecast":[
                     {"datetime":"2026-05-08","condition":"sunny","temperature":20,"templow":10},
                     {"datetime":"2026-05-09","condition":"rainy","temperature":18,"templow":11},
                     {"datetime":"2026-05-10","condition":"cloudy","temperature":17,"templow":12},
                     {"datetime":"2026-05-11","condition":"sunny","temperature":21,"templow":13}
                   ]}}
                """.trimIndent(),
            )
        val snap = WeatherSnapshot.fromEntity(entity)!!
        assertEquals(3, snap.forecast.size) // we cap at 3
        assertEquals("sunny", snap.forecast[0].condition)
        assertEquals(20.0, snap.forecast[0].tempHi!!, 0.001)
        assertEquals(10.0, snap.forecast[0].tempLo!!, 0.001)
    }

    @Test fun `missing temperature returns null in field`() {
        val entity =
            entity(
                """
                {"entity_id":"weather.forecast_home","state":"cloudy",
                 "attributes":{"humidity":80}}
                """.trimIndent(),
            )
        val snap = WeatherSnapshot.fromEntity(entity)!!
        assertEquals("cloudy", snap.state)
        assertNull(snap.temperatureC)
    }

    @Test fun `unknown state still returns snapshot`() {
        val entity =
            entity(
                """
                {"entity_id":"weather.x","state":"unknown","attributes":{}}
                """.trimIndent(),
            )
        val snap = WeatherSnapshot.fromEntity(entity)!!
        assertEquals("unknown", snap.state)
    }
}

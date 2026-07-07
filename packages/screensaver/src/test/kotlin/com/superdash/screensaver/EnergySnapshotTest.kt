package com.superdash.screensaver

import com.superdash.core.json.coreJson
import com.superdash.ha.EntityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EnergySnapshotTest {
    private val json = coreJson

    private fun entity(payload: String): EntityState = json.decodeFromString(EntityState.serializer(), payload)

    @Test fun `all null entities returns null`() {
        assertNull(EnergySnapshot.fromEntities(null, null, null))
    }

    @Test fun `single usage entity in watts`() {
        val usage = entity("""{"entity_id":"sensor.power","state":"1234","attributes":{"unit_of_measurement":"W"}}""")
        val snap = EnergySnapshot.fromEntities(usage, null, null)
        assertNotNull(snap)
        assertEquals(1234.0, snap!!.usageW!!, 0.001)
        assertNull(snap.solarW)
        assertNull(snap.gridW)
    }

    @Test fun `single solar entity in kilowatts converted to watts`() {
        val solar = entity("""{"entity_id":"sensor.solar","state":"3.4","attributes":{"unit_of_measurement":"kW"}}""")
        val snap = EnergySnapshot.fromEntities(null, solar, null)!!
        assertEquals(3400.0, snap.solarW!!, 0.001)
    }

    @Test fun `grid positive means importing`() {
        val grid = entity("""{"entity_id":"sensor.grid","state":"500","attributes":{"unit_of_measurement":"W"}}""")
        val snap = EnergySnapshot.fromEntities(null, null, grid)!!
        assertEquals(500.0, snap.gridW!!, 0.001)
    }

    @Test fun `grid negative means exporting`() {
        val grid = entity("""{"entity_id":"sensor.grid","state":"-1200","attributes":{"unit_of_measurement":"W"}}""")
        val snap = EnergySnapshot.fromEntities(null, null, grid)!!
        assertEquals(-1200.0, snap.gridW!!, 0.001)
    }

    @Test fun `unknown state yields null snapshot when only field`() {
        val usage = entity("""{"entity_id":"sensor.power","state":"unknown","attributes":{"unit_of_measurement":"W"}}""")
        val snap = EnergySnapshot.fromEntities(usage, null, null)
        assertNull(snap)
    }

    @Test fun `unavailable state yields null snapshot when only field`() {
        val usage = entity("""{"entity_id":"sensor.power","state":"unavailable","attributes":{}}""")
        assertNull(EnergySnapshot.fromEntities(usage, null, null))
    }

    @Test fun `missing unit defaults to watts`() {
        val usage = entity("""{"entity_id":"sensor.power","state":"800","attributes":{}}""")
        val snap = EnergySnapshot.fromEntities(usage, null, null)!!
        assertEquals(800.0, snap.usageW!!, 0.001)
    }

    @Test fun `mixed entities populate three fields`() {
        val usage = entity("""{"entity_id":"sensor.power","state":"1500","attributes":{"unit_of_measurement":"W"}}""")
        val solar = entity("""{"entity_id":"sensor.solar","state":"2.0","attributes":{"unit_of_measurement":"kW"}}""")
        val grid = entity("""{"entity_id":"sensor.grid","state":"-500","attributes":{"unit_of_measurement":"W"}}""")
        val snap = EnergySnapshot.fromEntities(usage, solar, grid)!!
        assertEquals(1500.0, snap.usageW!!, 0.001)
        assertEquals(2000.0, snap.solarW!!, 0.001)
        assertEquals(-500.0, snap.gridW!!, 0.001)
    }

    @Test fun `non-numeric state yields null snapshot when only field`() {
        val usage = entity("""{"entity_id":"sensor.power","state":"hello","attributes":{"unit_of_measurement":"W"}}""")
        assertNull(EnergySnapshot.fromEntities(usage, null, null))
    }
}

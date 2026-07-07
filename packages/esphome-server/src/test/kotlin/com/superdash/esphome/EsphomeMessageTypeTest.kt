package com.superdash.esphome

import org.junit.Assert.assertEquals
import org.junit.Test

class EsphomeMessageTypeTest {
    @Test
    fun `constants include ids for sensor number and select entities`() {
        assertEquals(16, EsphomeMessageType.LIST_ENTITIES_SENSOR_RESPONSE)
        assertEquals(25, EsphomeMessageType.SENSOR_STATE_RESPONSE)
        assertEquals(18, EsphomeMessageType.LIST_ENTITIES_TEXT_SENSOR_RESPONSE)
        assertEquals(27, EsphomeMessageType.TEXT_SENSOR_STATE_RESPONSE)
        assertEquals(49, EsphomeMessageType.LIST_ENTITIES_NUMBER_RESPONSE)
        assertEquals(50, EsphomeMessageType.NUMBER_STATE_RESPONSE)
        assertEquals(51, EsphomeMessageType.NUMBER_COMMAND_REQUEST)
        assertEquals(52, EsphomeMessageType.LIST_ENTITIES_SELECT_RESPONSE)
        assertEquals(53, EsphomeMessageType.SELECT_STATE_RESPONSE)
        assertEquals(54, EsphomeMessageType.SELECT_COMMAND_REQUEST)
    }
}

package com.superdash.esphome

import org.junit.Assert.assertEquals
import org.junit.Test

class MacFromAndroidIdTest {
    @Test
    fun `derives locally administered unicast mac from stable ids`() {
        val expectedByStableId =
            mapOf(
                "0123456789abcdef" to "02:23:45:67:89:AB",
                "123e4567-e89b-12d3-a456-426614174000" to "12:3E:45:67:E8:9B",
                "0fedcba987654321" to "0E:ED:CB:A9:87:65",
                "abc" to "AA:C0:00:00:00:00",
            )
        expectedByStableId.forEach { (stableId, expectedMac) ->
            assertEquals(stableId, expectedMac, deriveMacFromStableId(stableId))
        }
    }
}

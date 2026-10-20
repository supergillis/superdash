package com.superdash.device

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInfoTest {
    @Test
    fun `stable device id uses the fallback only when android id is null or blank`() {
        val casesToExpectedId =
            listOf(
                null to "persisted",
                "" to "persisted",
                "   " to "persisted",
                "0123456789abcdef" to "0123456789abcdef",
            )
        casesToExpectedId.forEach { (androidId, expectedId) ->
            assertEquals(androidId, expectedId, resolveStableDeviceId(androidId) { "persisted" })
        }
    }
}

package com.superdash.ha

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class HaServiceCallClientTest {
    @Test fun `callService sends domain service target and service data`() =
        runTest {
            var capturedType: String? = null
            var capturedPayload: JsonObject? = null
            val client =
                HaServiceCallClient(
                    call = { type, params ->
                        capturedType = type
                        capturedPayload = buildJsonObject(params)
                        successFrame(
                            buildJsonObject {
                                put("context", "abc")
                            },
                        )
                    },
                )

            val result =
                client.callService(
                    HaServiceCall(
                        domain = "light",
                        service = "turn_on",
                        target = HaServiceTarget(entityId = listOf("light.kitchen")),
                        serviceData =
                            buildJsonObject {
                                put("brightness_pct", 75)
                            },
                        returnResponse = true,
                    ),
                )

            assertEquals("call_service", capturedType)
            assertEquals("\"light\"", capturedPayload?.get("domain").toString())
            assertEquals("\"turn_on\"", capturedPayload?.get("service").toString())
            assertEquals(
                "\"light.kitchen\"",
                capturedPayload
                    ?.get("target")
                    ?.jsonObject
                    ?.get("entity_id")
                    ?.jsonArray
                    ?.single()
                    .toString(),
            )
            assertEquals(
                "75",
                capturedPayload
                    ?.get("service_data")
                    ?.jsonObject
                    ?.get("brightness_pct")
                    .toString(),
            )
            assertEquals(true, result.success)
        }

    @Test fun `callService surfaces HA error frame`() =
        runTest {
            val client =
                HaServiceCallClient(
                    call = { _, _ ->
                        buildJsonObject {
                            put("id", 1)
                            put("type", "result")
                            put("success", false)
                            putJsonObject("error") {
                                put("code", "service_not_found")
                                put("message", "Service not found")
                            }
                        }
                    },
                )

            try {
                client.callService(HaServiceCall(domain = "light", service = "missing"))
                fail("expected exception")
            } catch (e: HaServiceCallException) {
                assertEquals("service_not_found", e.code)
                assertEquals("Service not found", e.message)
            }
        }

    @Test fun `callService surfaces command timeout`() =
        runTest {
            val client =
                HaServiceCallClient(
                    call = { _, _ ->
                        throw HaCommandTimeoutException("call_service", 42)
                    },
                )

            try {
                client.callService(HaServiceCall(domain = "light", service = "turn_on"))
                fail("expected timeout")
            } catch (e: HaCommandTimeoutException) {
                assertEquals("call_service", e.commandType)
                assertEquals(42, e.commandId)
            }
        }

    private fun successFrame(result: JsonObject): JsonObject =
        buildJsonObject {
            put("id", 1)
            put("type", "result")
            put("success", true)
            put("result", result)
        }
}

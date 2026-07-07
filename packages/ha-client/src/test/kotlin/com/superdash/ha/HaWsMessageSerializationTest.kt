package com.superdash.ha

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HaWsMessageSerializationTest {
    private val json = haJson

    @Test fun `parses auth required`() {
        val frame =
            json.decodeFromString<HaWsFrame>(
                """{"type":"auth_required","ha_version":"2025.5.2"}""",
            )
        assertTrue(frame is AuthRequired)
        assertEquals("2025.5.2", (frame as AuthRequired).haVersion)
    }

    @Test fun `parses auth ok`() {
        val frame =
            json.decodeFromString<HaWsFrame>(
                """{"type":"auth_ok","ha_version":"2025.5.2"}""",
            )
        assertTrue(frame is AuthOk)
    }

    @Test fun `parses auth invalid`() {
        val frame =
            json.decodeFromString<HaWsFrame>(
                """{"type":"auth_invalid","message":"Invalid access token or password"}""",
            )
        assertTrue(frame is AuthInvalid)
        assertEquals("Invalid access token or password", (frame as AuthInvalid).message)
    }

    @Test fun `parses pong`() {
        val frame =
            json.decodeFromString<HaWsFrame>(
                """{"id":42,"type":"pong"}""",
            )
        assertTrue(frame is Pong)
        assertEquals(42, (frame as Pong).id)
    }

    @Test fun `parses result success`() {
        val frame =
            json.decodeFromString<HaWsFrame>(
                """{"id":1,"type":"result","success":true,"result":[{"entity_id":"sun.sun","state":"above_horizon"}]}""",
            )
        assertTrue(frame is ResultFrame)
        val r = frame as ResultFrame
        assertEquals(1, r.id)
        assertTrue(r.success)
    }

    @Test fun `decodes typed result list`() {
        val frame =
            haJson.decodeFromString<ResultFrame>(
                """{"id":1,"type":"result","success":true,"result":[{"entity_id":"light.office","state":"on","attributes":{"friendly_name":"Office"}}]}""",
            )

        val states = frame.requireResultList<EntityState>("get_states")

        assertEquals(listOf("light.office"), states.map { state -> state.entityId })
        assertEquals("on", states.single().state)
    }

    @Test fun `decodes typed registry result list`() {
        val frame =
            haJson.decodeFromString<ResultFrame>(
                """
                {
                  "id": 2,
                  "type": "result",
                  "success": true,
                  "result": [
                    {
                      "entity_id": "light.office",
                      "aliases": ["Desk Lights"],
                      "options": {
                        "conversation": {
                          "should_expose": true
                        }
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )

        val entries = frame.requireResultList<HaEntityRegistryEntry>("entity registry")

        assertEquals(listOf("Desk Lights"), entries.single().aliases)
        assertEquals(
            true,
            entries
                .single()
                .options.conversation
                ?.shouldExpose,
        )
    }

    @Test fun `parses result error`() {
        val frame =
            json.decodeFromString<HaWsFrame>(
                """{"id":2,"type":"result","success":false,"error":{"code":"unknown_command","message":"Unknown command."}}""",
            )
        assertTrue(frame is ResultFrame)
        val r = frame as ResultFrame
        assertEquals(false, r.success)
        assertEquals("unknown_command", r.error?.code)
    }

    @Test fun `parses event state changed`() {
        val frame =
            json.decodeFromString<HaWsFrame>(
                """{"id":3,"type":"event","event":{"event_type":"state_changed","data":{"entity_id":"light.kitchen","old_state":null,"new_state":{"entity_id":"light.kitchen","state":"on","attributes":{"friendly_name":"Kitchen"}}},"origin":"LOCAL","time_fired":"2026-05-06T12:00:00.000000+00:00"}}""",
            )
        assertTrue(frame is EventFrame)
        val e = frame as EventFrame
        assertEquals(3, e.id)
        assertEquals("state_changed", e.event.eventType)
    }

    @Test fun `encodes auth command`() {
        val cmd: HaCommand = AuthCommand(accessToken = "tok123")
        val out = json.encodeToString(HaCommand.serializer(), cmd)
        assertTrue(out.contains("\"type\":\"auth\""))
        assertTrue(out.contains("\"access_token\":\"tok123\""))
    }

    @Test fun `encodes subscribe events`() {
        val cmd: HaCommand = SubscribeEventsCommand(id = 5, eventType = "state_changed")
        val out = json.encodeToString(HaCommand.serializer(), cmd)
        assertTrue(out.contains("\"type\":\"subscribe_events\""))
        assertTrue(out.contains("\"event_type\":\"state_changed\""))
    }

    @Test fun `encodes ping`() {
        val cmd: HaCommand = PingCommand(id = 99)
        val out = json.encodeToString(HaCommand.serializer(), cmd)
        assertTrue(out.contains("\"type\":\"ping\""))
        assertTrue(out.contains("\"id\":99"))
    }

    @Test fun `encodes metadata registry commands`() {
        val entityRegistry = json.encodeToString(HaCommand.serializer(), ListEntityRegistryCommand(id = 41))
        val voiceExposure = json.encodeToString(HaCommand.serializer(), ListVoiceExposedEntitiesCommand(id = 42))
        val deviceRegistry = json.encodeToString(HaCommand.serializer(), ListDeviceRegistryCommand(id = 43))

        assertTrue(entityRegistry.contains("\"type\":\"config/entity_registry/list\""))
        assertTrue(entityRegistry.contains("\"id\":41"))
        assertTrue(voiceExposure.contains("\"type\":\"homeassistant/expose_entity/list\""))
        assertTrue(voiceExposure.contains("\"id\":42"))
        assertTrue(deviceRegistry.contains("\"type\":\"config/device_registry/list\""))
        assertTrue(deviceRegistry.contains("\"id\":43"))
    }

    @Test fun `requireResult returns frame on success`() {
        val response =
            buildJsonObject {
                put("id", 1)
                put("type", "result")
                put("success", true)
                putJsonObject("result") {
                    put("value", "ok")
                }
            }

        val frame = response.requireResult("some_command")

        assertEquals(1, frame.id)
        assertTrue(frame.success)
    }

    @Test fun `requireResult throws HaResultException on failure`() {
        val response =
            buildJsonObject {
                put("id", 2)
                put("type", "result")
                put("success", false)
                putJsonObject("error") {
                    put("code", "not_found")
                    put("message", "missing entity")
                }
            }

        try {
            response.requireResult("some_command")
            fail("expected HaResultException")
        } catch (e: HaResultException) {
            assertEquals("some_command", e.commandName)
            assertEquals("not_found", e.code)
            assertEquals("missing entity", e.haMessage)
        }
    }

    @Test fun `requireResult defaults missing error fields`() {
        val response =
            buildJsonObject {
                put("id", 3)
                put("type", "result")
                put("success", false)
            }

        try {
            response.requireResult("some_command")
            fail("expected HaResultException")
        } catch (e: HaResultException) {
            assertEquals("unknown", e.code)
            assertEquals("some_command failed", e.haMessage)
        }
    }

    @Test fun `encodes call service command with target and service data`() {
        val cmd: HaCommand =
            CallServiceCommand(
                id = 12,
                domain = "light",
                service = "turn_on",
                target = HaServiceTarget(entityId = listOf("light.kitchen")),
                serviceData =
                    buildJsonObject {
                        put("brightness_pct", JsonPrimitive(75))
                    },
                returnResponse = true,
            )

        val out = json.encodeToString(HaCommand.serializer(), cmd)

        assertTrue(out.contains("\"type\":\"call_service\""))
        assertTrue(out.contains("\"domain\":\"light\""))
        assertTrue(out.contains("\"service\":\"turn_on\""))
        assertTrue(out.contains("\"entity_id\":[\"light.kitchen\"]"))
        assertTrue(out.contains("\"brightness_pct\":75"))
        assertTrue(out.contains("\"return_response\":true"))
    }
}

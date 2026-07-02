package com.superdash.voice

import com.superdash.ha.HaServiceCall
import com.superdash.ha.HaServiceCallException
import com.superdash.ha.HaServiceCallResult
import com.superdash.ha.HaServiceTarget
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.executors.HaServiceCallExecutor
import com.superdash.voice.action.executors.executeAction
import com.superdash.voice.intent.LocalIntentAction
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HaServiceCallExecutorTest {
    private val serviceCall =
        LocalIntentAction.ServiceCall(
            transcript = "turn on kitchen lights",
            call =
                HaServiceCall(
                    domain = "light",
                    service = "turn_on",
                    target = HaServiceTarget(entityId = listOf("light.kitchen")),
                ),
        )

    @Test fun `successful service call returns ActionComplete with action_done response`() =
        runTest {
            val executor =
                HaServiceCallExecutor { _ ->
                    HaServiceCallResult(
                        success = true,
                        result = buildJsonObject { put("result_field", JsonPrimitive("ok")) },
                    )
                }

            val event = executor.executeAction(serviceCall) as VoiceActionEvent.ActionComplete

            assertEquals("turn on kitchen lights", event.transcript)
            assertEquals(JsonPrimitive("action_done"), event.response["response_type"])
        }

    @Test fun `HaServiceCallException maps to Error with code and message`() =
        runTest {
            val executor =
                HaServiceCallExecutor { _ ->
                    throw HaServiceCallException("call_failed", "service unavailable")
                }

            val event = executor.executeAction(serviceCall) as VoiceActionEvent.Error

            assertEquals("call_failed", event.code)
            assertEquals("service unavailable", event.message)
        }
}

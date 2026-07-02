package com.superdash.voice.action.executors

import com.superdash.ha.HaServiceCall
import com.superdash.ha.HaServiceCallException
import com.superdash.ha.HaServiceCallResult
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.intent.LocalIntentAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

fun interface HaServiceCallExecutor {
    suspend fun execute(call: HaServiceCall): HaServiceCallResult
}

suspend fun HaServiceCallExecutor.executeAction(
    action: LocalIntentAction.ServiceCall,
): VoiceActionEvent =
    try {
        val result = execute(action.call)
        VoiceActionEvent.ActionComplete(
            transcript = action.transcript,
            response = result.toVoiceResponse(),
        )
    } catch (e: HaServiceCallException) {
        VoiceActionEvent.Error(e.code, e.message ?: "call_service failed")
    }

private fun HaServiceCallResult.toVoiceResponse(): JsonObject =
    buildJsonObject {
        put("response_type", JsonPrimitive("action_done"))
        val serviceResult = result
        if (serviceResult != null) {
            put("result", serviceResult)
        }
    }

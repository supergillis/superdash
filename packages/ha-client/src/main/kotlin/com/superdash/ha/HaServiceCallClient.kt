package com.superdash.ha

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

class HaServiceCallClient(
    private val call: suspend (type: String, JsonObjectBuilder.() -> Unit) -> JsonObject,
) {
    suspend fun callService(request: HaServiceCall): HaServiceCallResult {
        val response =
            call("call_service") {
                put("domain", request.domain)
                put("service", request.service)
                if (request.serviceData != null) {
                    put("service_data", request.serviceData)
                }
                if (request.target != null) {
                    put("target", haJson.encodeToJsonElement(request.target))
                }
                if (request.returnResponse != null) {
                    put("return_response", request.returnResponse)
                }
            }
        val frame =
            try {
                response.requireResult("call_service")
            } catch (e: HaResultException) {
                throw HaServiceCallException(code = e.code, message = e.haMessage)
            }
        return HaServiceCallResult(success = true, result = frame.result)
    }

    companion object {
        fun fromHaWebSocketClient(ws: HaWebSocketClient): HaServiceCallClient =
            HaServiceCallClient(call = { type, params -> ws.callResult(type, params) })
    }
}

class HaServiceCallException(
    val code: String,
    message: String,
) : Exception(message)

package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Thrown for a non-2xx token endpoint response. [error] is the RFC 6749 section 5.2 error code, when present. */
class HaTokenExchangeException(
    val status: Int,
    val error: String? = null,
) : Exception("HA token request failed with HTTP $status" + (error?.let { ", error $it" } ?: ""))

internal suspend fun postTokenRequest(
    httpClient: HttpClient,
    baseUrl: String,
    grantParameters: Map<String, String>,
): HttpResponse =
    httpClient.submitForm(
        url = "$baseUrl/auth/token",
        formParameters =
            parameters {
                grantParameters.forEach { (name, value) -> append(name, value) }
                append("client_id", "$baseUrl/")
            },
    )

internal suspend fun HttpResponse.toTokenExchangeException(): HaTokenExchangeException {
    val body = bodyAsText()
    val errorCode =
        runCatching {
            Json
                .parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
    return HaTokenExchangeException(status.value, errorCode)
}

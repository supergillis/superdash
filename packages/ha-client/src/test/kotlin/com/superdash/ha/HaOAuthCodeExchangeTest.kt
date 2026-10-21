package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class HaOAuthCodeExchangeTest {
    private class RecordingTokenStore : HaTokenStoreLike {
        var saved: HaTokens? = null

        override suspend fun load(): HaTokens? = saved

        override suspend fun save(tokens: HaTokens) {
            saved = tokens
        }

        override suspend fun clear() {
            saved = null
        }
    }

    private data class Case(
        val name: String,
        val engine: MockEngine,
        val expectedResult: Boolean,
        val expectSaved: Boolean,
    )

    @Test
    fun `reports whether the code exchange stored tokens`() {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val cases =
            listOf(
                Case(
                    "success",
                    MockEngine {
                        respond(
                            """{"access_token":"a","refresh_token":"r","expires_in":1800,"token_type":"Bearer"}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    },
                    expectedResult = true,
                    expectSaved = true,
                ),
                Case(
                    "invalid grant",
                    MockEngine { respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, jsonHeaders) },
                    expectedResult = false,
                    expectSaved = false,
                ),
                Case(
                    "network failure",
                    MockEngine { throw IOException("offline") },
                    expectedResult = false,
                    expectSaved = false,
                ),
            )
        cases.forEach { case ->
            val store = RecordingTokenStore()
            val client = HttpClient(case.engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val result = runBlocking { exchangeAndSaveAuthCode(client, store, "https://ha.local", "code") }
            assertEquals(case.name, case.expectedResult, result)
            assertEquals(case.name, case.expectSaved, store.saved != null)
        }
    }
}

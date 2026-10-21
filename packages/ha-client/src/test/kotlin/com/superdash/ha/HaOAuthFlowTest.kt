package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.URI

class HaOAuthFlowTest {
    @Test
    fun `authorizeUrl includes the provided state`() {
        val url = HaOAuthFlow.authorizeUrl("https://ha.local", state = "abc123")
        assertEquals("abc123", queryParam(url, "state"))
    }

    @Test
    fun `authorizeUrl url-encodes state`() {
        val url = HaOAuthFlow.authorizeUrl("https://ha.local", state = "a/b c")
        assertEquals("a/b c", queryParam(url, "state"))
    }

    @Test
    fun `isCallback returns code and state when both match`() {
        val callback = URI("https://ha.local/?auth_callback=1&code=AUTHCODE&state=NONCE")
        val result = HaOAuthFlow.isCallback(callback, "https://ha.local")
        assertEquals("AUTHCODE", result?.code)
        assertEquals("NONCE", result?.state)
    }

    @Test
    fun `isCallback returns null when auth_callback missing`() {
        val callback = URI("https://ha.local/?code=X&state=Y")
        assertNull(HaOAuthFlow.isCallback(callback, "https://ha.local"))
    }

    @Test
    fun `isCallback state is null when not present in callback`() {
        val callback = URI("https://ha.local/?auth_callback=1&code=X")
        val result = HaOAuthFlow.isCallback(callback, "https://ha.local")
        assertEquals("X", result?.code)
        assertNull(result?.state)
    }

    @Test
    fun `isCallback returns null for different host`() {
        val callback = URI("https://other.local/?auth_callback=1&code=abc123")
        assertNull(HaOAuthFlow.isCallback(callback, "https://ha.local"))
    }

    @Test
    fun `isCallback matches host case-insensitively`() {
        // RFC 3986: host is case-insensitive. HA may surface mixed-case hosts.
        val callback = URI("https://HA.Local/?auth_callback=1&code=abc123")
        val result = HaOAuthFlow.isCallback(callback, "https://ha.local")
        assertEquals("abc123", result?.code)
    }

    @Test
    fun `isCallback matches scheme case-insensitively`() {
        val callback = URI("HTTPS://ha.local/?auth_callback=1&code=abc123")
        val result = HaOAuthFlow.isCallback(callback, "https://ha.local")
        assertEquals(HaOAuthFlow.CallbackParams(code = "abc123", state = null), result)
    }

    @Test
    fun `exchangeCode throws a typed error carrying the status and error code`() {
        val cases =
            listOf(
                Triple(
                    HttpStatusCode.BadRequest,
                    """{"error":"invalid_request","error_description":"Invalid code"}""",
                    "invalid_request",
                ),
                Triple(HttpStatusCode.InternalServerError, "upstream down", null),
            )
        cases.forEach { (status, body, expectedError) ->
            val thrown =
                assertThrows(HaTokenExchangeException::class.java) {
                    runBlocking { HaOAuthFlow.exchangeCode(clientResponding(status, body), "https://ha.local", "code") }
                }
            assertEquals(status.value, thrown.status)
            assertEquals(expectedError, thrown.error)
        }
    }

    @Test
    fun `isCallback matches an explicit default port against an implicit one`() {
        val callback = URI("https://ha.example.com:443/?auth_callback=1&code=abc123")
        val result = HaOAuthFlow.isCallback(callback, "https://ha.example.com")
        assertEquals(HaOAuthFlow.CallbackParams(code = "abc123", state = null), result)
    }

    @Test
    fun `isCallback returns null for different port`() {
        val callback = URI("https://ha.local:9999/?auth_callback=1&code=abc123")
        assertNull(HaOAuthFlow.isCallback(callback, "https://ha.local"))
    }

    @Test
    fun `isCallback returns null when no code param`() {
        val callback = URI("https://ha.local/?auth_callback=1")
        assertNull(HaOAuthFlow.isCallback(callback, "https://ha.local"))
    }

    private fun clientResponding(status: HttpStatusCode, body: String) =
        HttpClient(
            MockEngine {
                respond(
                    content = ByteReadChannel(body),
                    status = status,
                    headers = headersOf("Content-Type", "application/json"),
                )
            },
        ) { install(ContentNegotiation) { json() } }

    private fun queryParam(uri: URI, key: String): String? =
        uri.rawQuery
            ?.split("&")
            ?.map { it.split("=", limit = 2) }
            ?.firstOrNull { it[0] == key }
            ?.let { java.net.URLDecoder.decode(it[1], "UTF-8") }
}

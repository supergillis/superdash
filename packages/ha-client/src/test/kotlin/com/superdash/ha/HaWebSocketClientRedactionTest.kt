package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The debug screen renders recentEvents verbatim, so the long-lived HA access
 *  token must never survive into a recorded frame. */
class HaWebSocketClientRedactionTest {
    @Test
    fun `access token in auth frame is redacted`() {
        val client = newClient()
        val line = "→ {\"type\":\"auth\",\"access_token\":\"eyJhbGc.super-secret.value\"}"

        val redacted = client.redactSecretsForTest(line)

        assertFalse("token leaked: $redacted", redacted.contains("super-secret"))
        assertEquals(
            "→ {\"type\":\"auth\",\"access_token\":\"<redacted>\"}",
            redacted,
        )
    }

    @Test
    fun `access token is redacted with whitespace around the field`() {
        val client = newClient()
        val line = "{ \"access_token\" : \"tok-123\" }"

        val redacted = client.redactSecretsForTest(line)

        assertFalse(redacted.contains("tok-123"))
        assertEquals("{ \"access_token\" : \"<redacted>\" }", redacted)
    }

    @Test
    fun `token containing an escaped quote is fully redacted`() {
        val client = newClient()
        val line = "{\"access_token\":\"aa\\\"bb\",\"type\":\"auth\"}"

        val redacted = client.redactSecretsForTest(line)

        assertFalse("token tail leaked: $redacted", redacted.contains("bb"))
        assertEquals("{\"access_token\":\"<redacted>\",\"type\":\"auth\"}", redacted)
    }

    @Test
    fun `frames without a token are left unchanged`() {
        val client = newClient()
        val line = "← {\"type\":\"event\",\"event\":{\"entity_id\":\"light.kitchen\"}}"

        assertEquals(line, client.redactSecretsForTest(line))
    }

    private fun newClient(): HaWebSocketClient {
        val haUrl = MutableStateFlow<String?>(null)
        val engine = MockEngine { _ -> respond(ByteReadChannel.Empty, HttpStatusCode.BadRequest) }
        val httpClient = HttpClient(engine)
        val tokens =
            HaTokenProvider(
                store = InMemoryHaTokenStore(),
                httpClient = httpClient,
                baseUrl = { haUrl.value ?: "" },
            )
        return HaWebSocketClient(haUrl = haUrl, tokens = tokens, httpClient = httpClient)
    }

    private class InMemoryHaTokenStore : HaTokenStoreLike {
        private var tokens: HaTokens? =
            HaTokens(
                accessToken = "access",
                refreshToken = "refresh",
                expiresAtEpochMs = Long.MAX_VALUE / 2,
            )

        override suspend fun load(): HaTokens? = tokens

        override suspend fun save(tokens: HaTokens) {
            this.tokens = tokens
        }

        override suspend fun clear() {
            tokens = null
        }
    }
}

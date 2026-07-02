package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class HaTokenProviderTest {
    private val baseUrl = "http://hass.local:8123"
    private val now = Instant.parse("2026-05-06T12:00:00Z")
    private val fixedClock =
        object : Clock {
            override fun now() = now
        }

    private fun providerWith(
        initial: HaTokens?,
        respondWith: HaTokenResponse,
    ): Pair<HaTokenProvider, RecordingTokenStore> {
        val store = RecordingTokenStore(initial)
        val engine =
            MockEngine { _ ->
                val refreshPart = respondWith.refreshToken?.let { "\"refresh_token\":\"$it\"," } ?: ""
                val body = """{"access_token":"${respondWith.accessToken}",$refreshPart"expires_in":${respondWith.expiresIn},"token_type":"Bearer"}"""
                respond(
                    content = ByteReadChannel(body),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json() } }
        return HaTokenProvider(store, httpClient, { baseUrl }, fixedClock) to store
    }

    @Test fun `returns cached when within skew`() =
        runBlocking {
            val (p, _) =
                providerWith(
                    HaTokens("cached", "r1", (now + 600.seconds).toEpochMilliseconds()),
                    HaTokenResponse("new", "r2", 1800),
                )
            assertEquals("cached", p.get())
        }

    @Test fun `refreshes when within skew window`() =
        runBlocking {
            val (p, store) =
                providerWith(
                    HaTokens("near-expiry", "r1", (now + 30.seconds).toEpochMilliseconds()),
                    HaTokenResponse("fresh", "r2", 1800),
                )
            val tok = p.get()
            assertEquals("fresh", tok)
            assertEquals("fresh", store.lastSaved!!.accessToken)
            assertEquals("r2", store.lastSaved!!.refreshToken)
        }

    @Test fun `refresh response without refresh token preserves old`() =
        runBlocking {
            val (p, store) =
                providerWith(
                    HaTokens("near-expiry", "r-old", (now + 10.seconds).toEpochMilliseconds()),
                    HaTokenResponse("fresh", refreshToken = null, expiresIn = 1800),
                )
            p.get()
            assertEquals("r-old", store.lastSaved!!.refreshToken)
        }

    @Test fun `get throws when not authenticated`() {
        val (p, _) = providerWith(initial = null, HaTokenResponse("x", "y", 1800))
        assertThrows(NotAuthenticatedException::class.java) { runBlocking { p.get() } }
    }

    @Test fun `force refresh returns fresh token`() =
        runBlocking {
            val (p, store) =
                providerWith(
                    HaTokens("old", "r1", (now + 9999.seconds).toEpochMilliseconds()),
                    HaTokenResponse("forced", "r2", 1800),
                )
            val tok = p.forceRefresh()
            assertEquals("forced", tok)
            assertEquals("forced", store.lastSaved!!.accessToken)
        }

    @Test fun `expires at advances after refresh`() =
        runBlocking {
            val (p, store) =
                providerWith(
                    HaTokens("old", "r1", (now + 30.seconds).toEpochMilliseconds()),
                    HaTokenResponse("new", "r2", 1800),
                )
            p.get()
            val expected = (now + 1800.seconds).toEpochMilliseconds()
            assertEquals(expected, store.lastSaved!!.expiresAtEpochMs)
        }
}

/** In-memory store, records the last save for assertion. */
class RecordingTokenStore(
    initial: HaTokens?,
) : HaTokenStoreLike {
    private val state = MutableStateFlow(initial)
    var lastSaved: HaTokens? = null
        private set

    override suspend fun load(): HaTokens? = state.value

    override suspend fun save(tokens: HaTokens) {
        state.value = tokens
        lastSaved = tokens
    }

    override suspend fun clear() {
        state.value = null
    }
}

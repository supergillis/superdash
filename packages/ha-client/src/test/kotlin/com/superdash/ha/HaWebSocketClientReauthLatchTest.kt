package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Reproduces issue #27: a transient IO failure while refreshing the HA token
 *  must NOT be classified as a "needs reauth" state. Doing so parks the
 *  reconnect loop on `reconnectSignal.first()` with no backoff timer, leaving
 *  the socket permanently dead until an unrelated OS network callback fires.
 *
 *  Only a genuine "no stored credentials" ([NotAuthenticatedException]) is a
 *  real reauth condition. Everything else must propagate so the reconnect loop
 *  retries it with the existing exponential backoff. */
class HaWebSocketClientReauthLatchTest {
    private val haUrl = MutableStateFlow<String?>("http://ha.local:8123")

    private fun clientWith(provider: HaTokenProvider): HaWebSocketClient {
        val engine = MockEngine { throw IOException("unused") }
        return HaWebSocketClient(haUrl = haUrl, tokens = provider, httpClient = HttpClient(engine))
    }

    /** A transient IO failure during token refresh must not latch reauth. */
    @Test
    fun `transient IO during token refresh does not latch reauth`() {
        // Expired token -> get() triggers a refresh; the refresh HTTP call blips.
        val store = InMemoryHaTokenStore(HaTokens("stale", "refresh", expiresAtEpochMs = 0L))
        val refreshEngine = MockEngine { throw IOException("LAN blip during token refresh") }
        val provider =
            HaTokenProvider(
                store = store,
                httpClient = HttpClient(refreshEngine),
                baseUrl = { haUrl.value ?: "" },
            )
        val client = clientWith(provider)

        val thrown =
            assertThrows(Throwable::class.java) {
                runBlocking { client.acquireTokenForConnect() }
            }
        assertFalse(
            "transient IO must not be classified as a reauth latch, got ${thrown::class.simpleName}",
            thrown is HaWebSocketClient.NotAuthenticatedExceptionWrapper,
        )
    }

    /** A genuine "no stored credentials" is the only real reauth condition. */
    @Test
    fun `missing credentials are classified as reauth`() {
        val store = InMemoryHaTokenStore(null)
        val provider =
            HaTokenProvider(
                store = store,
                httpClient = HttpClient(MockEngine { throw IOException("unused") }),
                baseUrl = { haUrl.value ?: "" },
            )
        val client = clientWith(provider)

        val thrown =
            assertThrows(Throwable::class.java) {
                runBlocking { client.acquireTokenForConnect() }
            }
        assertTrue(
            "missing credentials must map to reauth, got ${thrown::class.simpleName}",
            thrown is HaWebSocketClient.NotAuthenticatedExceptionWrapper,
        )
    }

    private class InMemoryHaTokenStore(
        private var tokens: HaTokens?,
    ) : HaTokenStoreLike {
        override suspend fun load(): HaTokens? = tokens

        override suspend fun save(tokens: HaTokens) {
            this.tokens = tokens
        }

        override suspend fun clear() {
            tokens = null
        }
    }
}

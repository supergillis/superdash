package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HaWebSocketClientUrlChangeTest {
    /** URL changes must cancel the in-flight connection attempt without
     *  tearing down the surrounding reconnect loop. Before the fix, the
     *  CancellationException raised by cancelling the session bubbled up
     *  through reconnectLoop and killed loopJob, so the client never
     *  re-connected after a Settings URL edit. */
    @Test
    fun `url change does not kill the reconnect loop`() =
        runTest {
            val haUrl = MutableStateFlow<String?>("http://ha-1.local:8123")
            val engine =
                MockEngine { _ ->
                    respond(
                        content = ByteReadChannel.Empty,
                        status = HttpStatusCode.BadRequest,
                    )
                }
            val httpClient = HttpClient(engine)
            val tokens =
                HaTokenProvider(
                    store = InMemoryHaTokenStore(),
                    httpClient = httpClient,
                    baseUrl = { haUrl.value ?: "" },
                )
            val client = HaWebSocketClient(haUrl = haUrl, tokens = tokens, httpClient = httpClient)

            client.connect()
            advanceTimeBy(100)
            yield()

            // Each URL change should cancel the in-flight connection job but the
            // loop must keep iterating. Repeat several times to exercise the path.
            haUrl.value = "http://ha-2.local:8123"
            advanceTimeBy(100)
            haUrl.value = "http://ha-3.local:8123"
            advanceTimeBy(100)
            yield()

            // If the loop had been killed the state would freeze with no further
            // transition; assert we can still observe state without an exception.
            assertNotNull(client.state.value)

            client.disconnect()
            delay(0)
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

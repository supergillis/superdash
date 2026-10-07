package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HaWebSocketClientProbeTest {
    private data class ProbeCase(
        val name: String,
        val pongAfterMs: Long?,
        val expectedTimeouts: Int,
    )

    @Test
    fun `checkConnection drops the socket only when no pong arrives within 5 seconds`() {
        listOf(
            ProbeCase("immediate pong", pongAfterMs = 0L, expectedTimeouts = 0),
            ProbeCase("pong just inside the window", pongAfterMs = 4_999L, expectedTimeouts = 0),
            ProbeCase("pong never arrives", pongAfterMs = null, expectedTimeouts = 1),
        ).forEach { case ->
            runTest {
                val client = newClient()
                var pings = 0
                val timedOutPingIds = mutableListOf<Int>()
                val connectionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
                connectionScope.launch {
                    client.serveProbes(
                        awaitPong = {
                            pings += 1
                            if (case.pongAfterMs == null) {
                                awaitCancellation()
                            }
                            delay(case.pongAfterMs)
                        },
                        onTimeout = { pingId -> timedOutPingIds += pingId },
                    )
                }

                client.checkConnection()
                advanceTimeBy(4_999L)
                assertEquals("${case.name}: no timeout before 5 s", emptyList<Int>(), timedOutPingIds)
                advanceTimeBy(2L)

                assertEquals("${case.name}: one ping sent", 1, pings)
                assertEquals("${case.name}: timeouts", case.expectedTimeouts, timedOutPingIds.size)
                connectionScope.cancel()
            }
        }
    }

    @Test
    fun `forceReconnect cancels the live connection and reconnects without backoff`() =
        runTest {
            val attempts = Channel<Unit>(Channel.UNLIMITED)
            val client =
                newClient(this) {
                    attempts.trySend(Unit)
                    awaitCancellation()
                }

            client.connect()
            attempts.receive()
            client.forceReconnect()
            attempts.receive()

            assertTrue("reconnect must not wait out the 1 s backoff, took $currentTime ms", currentTime < 1_000L)
            client.disconnect()
        }

    @Test
    fun `checkConnection wakes a loop that is waiting to retry`() =
        runTest {
            val attempts = Channel<Unit>(Channel.UNLIMITED)
            val client =
                newClient(this) {
                    attempts.trySend(Unit)
                    throw IOException("down")
                }

            client.connect()
            attempts.receive()
            client.state.first { it is HaConnectionState.Failed }
            client.checkConnection()
            attempts.receive()

            assertTrue("retry must not wait out the 1 s backoff, took $currentTime ms", currentTime < 1_000L)
            client.disconnect()
        }

    private fun newClient(
        testScope: TestScope? = null,
        handler: suspend () -> Nothing = { throw IOException("unused") },
    ): HaWebSocketClient {
        val haUrl = MutableStateFlow<String?>("http://ha.local:8123")
        val httpClient = HttpClient(MockEngine { _ -> handler() }) { install(WebSockets) }
        val tokens =
            HaTokenProvider(
                store = InMemoryHaTokenStore(),
                httpClient = httpClient,
                baseUrl = { haUrl.value ?: "" },
            )
        return if (testScope == null) {
            HaWebSocketClient(haUrl = haUrl, tokens = tokens, httpClient = httpClient)
        } else {
            HaWebSocketClient(
                haUrl = haUrl,
                tokens = tokens,
                httpClient = httpClient,
                context = StandardTestDispatcher(testScope.testScheduler),
            )
        }
    }

    private class InMemoryHaTokenStore : HaTokenStoreLike {
        private var tokens: HaTokens? =
            HaTokens(accessToken = "access", refreshToken = "refresh", expiresAtEpochMs = Long.MAX_VALUE / 2)

        override suspend fun load(): HaTokens? = tokens

        override suspend fun save(tokens: HaTokens) {
            this.tokens = tokens
        }

        override suspend fun clear() {
            tokens = null
        }
    }
}

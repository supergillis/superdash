package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class HaWebSocketClientPingLifecycleTest {
    /** The ping loop must end when its enclosing scope is cancelled. The
     *  production code launches the loop on the per-connection scope so that
     *  ending the connection (via session close, URL change, or disconnect())
     *  also ends the loop. */
    @Test
    fun `ping loop ends when its enclosing scope is cancelled`() =
        runTest {
            val client = newClient()
            val sentPings = AtomicInteger(0)
            val timeouts = AtomicInteger(0)
            val connectionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val pingLoop =
                connectionScope.launch {
                    client.runPingLoopForTest(
                        sendPing = { sentPings.incrementAndGet() },
                        respond = { pingId -> client.deliverPongForTest(pingId) },
                        onTimeout = { timeouts.incrementAndGet() },
                    )
                }

            advanceTimeBy(PING_INTERVAL_MS_FOR_TEST * 3 + 10)
            yield()
            assertTrue("expected at least 3 pings, got ${sentPings.get()}", sentPings.get() >= 3)

            connectionScope.cancel()
            pingLoop.join()

            val before = sentPings.get()
            advanceTimeBy(PING_INTERVAL_MS_FOR_TEST * 5)
            yield()
            assertEquals("no further pings after scope cancellation", before, sentPings.get())
            assertEquals("never timed out in this scenario", 0, timeouts.get())
        }

    /** When pong does not arrive within the timeout, the ping loop must report
     *  via its onTimeout callback and exit. */
    @Test
    fun `ping loop reports timeout when pong does not arrive`() =
        runTest {
            val client = newClient()
            val sentPings = AtomicInteger(0)
            val timeouts = AtomicInteger(0)
            val connectionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val pingLoop =
                connectionScope.launch {
                    client.runPingLoopForTest(
                        sendPing = { sentPings.incrementAndGet() },
                        respond = { /* never ack so it times out */ },
                        onTimeout = { timeouts.incrementAndGet() },
                    )
                }

            advanceTimeBy(PING_INTERVAL_MS_FOR_TEST + PONG_TIMEOUT_MS_FOR_TEST + 1)
            yield()
            pingLoop.join()

            assertEquals(1, sentPings.get())
            assertEquals(1, timeouts.get())
            connectionScope.cancel()
        }

    /** disconnect() must not leak ping-loop coroutines onto the class scope.
     *  Before the fix the ping loop was launched on the class scope and could
     *  outlive its connection. We verify that even after exercising the
     *  reconnect loop and then disconnecting, the class scope has no active
     *  child jobs. */
    @Test
    fun `disconnect leaves no active children on the class scope`() =
        runTest {
            val haUrl = MutableStateFlow<String?>("http://ha.local:8123")
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
            advanceTimeBy(50)
            yield()
            haUrl.value = "http://ha-2.local:8123"
            advanceTimeBy(50)
            yield()

            client.disconnect()
            yield()
            advanceTimeBy(100)
            yield()

            val classJob = client.scopeForTest.coroutineContext[Job]!!
            val activeChildren = classJob.children.filter { it.isActive }.toList()
            assertTrue(
                "expected no active children on class scope after disconnect, found $activeChildren",
                activeChildren.isEmpty(),
            )
            assertFalse(client.isAnyJobRefActiveForTest)
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

    private companion object {
        const val PING_INTERVAL_MS_FOR_TEST = 100L
        const val PONG_TIMEOUT_MS_FOR_TEST = 50L
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

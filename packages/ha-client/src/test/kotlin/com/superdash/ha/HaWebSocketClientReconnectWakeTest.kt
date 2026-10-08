package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class HaWebSocketClientReconnectWakeTest {
    private class AttemptHarness(
        testScope: TestScope,
        onAttempt: suspend MockRequestHandleScope.(attemptNumber: Int, client: HaWebSocketClient) -> HttpResponseData,
    ) {
        val attemptStartTimes = mutableListOf<Long>()
        val client: HaWebSocketClient

        init {
            var capturedClient: HaWebSocketClient? = null
            val engineConfig =
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScope.testScheduler)
                    addHandler {
                        attemptStartTimes += testScope.currentTime
                        onAttempt(attemptStartTimes.size, requireNotNull(capturedClient))
                    }
                }
            val httpClient = HttpClient(MockEngine(engineConfig)) { install(WebSockets) }
            val haUrl = MutableStateFlow<String?>("http://ha.local:8123")
            val tokens =
                HaTokenProvider(
                    store = InMemoryHaTokenStore(),
                    httpClient = httpClient,
                    baseUrl = { haUrl.value ?: "" },
                )
            client =
                HaWebSocketClient(
                    haUrl = haUrl,
                    tokens = tokens,
                    httpClient = httpClient,
                    context = StandardTestDispatcher(testScope.testScheduler),
                )
            capturedClient = client
        }

        // A failing assertion must still stop the loop, or runTest spins on its pending retries forever.
        suspend fun withConnectedClient(body: suspend () -> Unit) {
            client.connect()
            try {
                body()
            } finally {
                client.disconnect()
            }
        }
    }

    @Test
    fun `a wake during an attempt that ends in reauth starts a second attempt`() {
        listOf(
            "not authenticated" to HaWebSocketClient.NotAuthenticatedExceptionWrapper(NotAuthenticatedException),
            "auth invalid" to AuthInvalidException("Invalid access token"),
        ).forEach { (name, reauthFailure) ->
            runTest {
                val harness =
                    AttemptHarness(this) { attemptNumber, client ->
                        if (attemptNumber == 1) {
                            client.checkConnection()
                            throw reauthFailure
                        }
                        throw IOException("down")
                    }

                harness.withConnectedClient {
                    runCurrent()

                    assertEquals("$name: attempt start times", listOf(0L, 0L), harness.attemptStartTimes)
                }
            }
        }
    }

    @Test
    fun `a wake during a failing attempt skips exactly one backoff step`() =
        runTest {
            val harness =
                AttemptHarness(this) { attemptNumber, client ->
                    if (attemptNumber == 1) {
                        client.checkConnection()
                    }
                    throw IOException("down")
                }

            harness.withConnectedClient {
                advanceTimeBy(6_000L)
                runCurrent()

                assertEquals(listOf(0L, 0L, 2_000L, 6_000L), harness.attemptStartTimes)
            }
        }

    @Test
    fun `forceReconnect leaves no pending wake that skips the next backoff`() =
        runTest {
            val harness =
                AttemptHarness(this) { attemptNumber, _ ->
                    if (attemptNumber == 1) {
                        awaitCancellation()
                    }
                    throw IOException("down")
                }

            harness.withConnectedClient {
                runCurrent()
                harness.client.forceReconnect()
                advanceTimeBy(1_000L)
                runCurrent()

                assertEquals(listOf(0L, 0L, 1_000L), harness.attemptStartTimes)
            }
        }

    @Test
    fun `a wake during the handshake does not skip the backoff after the connection drops`() =
        runTest {
            lateinit var server: FakeHaServer
            val harness =
                AttemptHarness(this) { attemptNumber, client ->
                    if (attemptNumber > 1) {
                        throw IOException("down")
                    }
                    server = FakeHaServer(currentCoroutineContext(), onAuthReceived = { client.checkConnection() })
                    HttpResponseData(
                        HttpStatusCode.SwitchingProtocols,
                        GMTDate(),
                        Headers.Empty,
                        HttpProtocolVersion.HTTP_1_1,
                        server,
                        currentCoroutineContext(),
                    )
                }

            harness.withConnectedClient {
                runCurrent()
                assertEquals(HaConnectionState.Connected("2026.1.0"), harness.client.state.value)
                server.dropConnection()
                advanceTimeBy(1_000L)
                runCurrent()

                assertEquals(listOf(0L, 1_000L), harness.attemptStartTimes)
            }
        }

    // Minimal HA server: greets with auth_required, accepts the token and answers every command with an empty result.
    private class FakeHaServer(
        callContext: CoroutineContext,
        private val onAuthReceived: () -> Unit,
    ) : WebSocketSession {
        private val toClient = Channel<Frame>(Channel.UNLIMITED)
        private val fromClient = Channel<Frame>(Channel.UNLIMITED)
        override val coroutineContext: CoroutineContext = callContext + Job()
        override var masking: Boolean = false
        override var maxFrameSize: Long = Long.MAX_VALUE
        override val incoming: ReceiveChannel<Frame> = toClient
        override val outgoing: SendChannel<Frame> = fromClient
        override val extensions: List<WebSocketExtension<*>> = emptyList()

        init {
            toClient.trySend(Frame.Text("""{"type":"auth_required","ha_version":"2026.1.0"}"""))
            launch {
                for (frame in fromClient) {
                    val command = Json.parseToJsonElement((frame as Frame.Text).readText()).jsonObject
                    if (command.getValue("type").jsonPrimitive.content == "auth") {
                        onAuthReceived()
                        toClient.trySend(Frame.Text("""{"type":"auth_ok","ha_version":"2026.1.0"}"""))
                    } else {
                        val id = command.getValue("id").jsonPrimitive.content
                        toClient.trySend(Frame.Text("""{"id":$id,"type":"result","success":true,"result":[]}"""))
                    }
                }
            }
        }

        fun dropConnection() {
            toClient.close()
        }

        override suspend fun flush() = Unit

        @Deprecated("Use cancel() instead.", replaceWith = ReplaceWith("cancel()"))
        override fun terminate() {
            cancel()
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

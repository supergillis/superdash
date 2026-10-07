package com.superdash.ha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HaWebSocketClientFrameRoutingTest {
    @Test
    fun `slow collector keeps every frame of its id behind a burst`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            val gate = CompletableDeferred<Unit>()
            val received = mutableListOf<JsonObject>()
            val collector =
                launch {
                    client.frames(5).collect { frame ->
                        gate.await()
                        received += frame
                    }
                }

            val burst = (1..65).map { index -> frame(id = 5, type = "event", seq = index) }
            val result = frame(id = 5, type = "result", seq = 66)
            (burst + result).forEach { client.routeFrame(it) }
            gate.complete(Unit)

            assertEquals(burst + result, received)
            collector.cancel()
        }

    @Test
    fun `frames reach only the collector that owns their id`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            val owner1 = mutableListOf<JsonObject>()
            val owner2 = mutableListOf<JsonObject>()
            val collector1 = launch { client.frames(1).collect { owner1 += it } }
            val collector2 = launch { client.frames(2).collect { owner2 += it } }

            val incoming =
                listOf(
                    frame(id = 1, type = "event", seq = 1),
                    frame(id = 2, type = "event", seq = 2),
                    frame(id = 99, type = "event", seq = 3),
                    buildJsonObject { put("type", JsonPrimitive("auth_ok")) },
                    frame(id = 1, type = "result", seq = 4),
                )
            incoming.forEach { client.routeFrame(it) }

            assertEquals(listOf(incoming[0], incoming[4]), owner1)
            assertEquals(listOf(incoming[1]), owner2)
            assertEquals(2, client.activeRouteCountForTest)

            collector1.cancel()
            assertEquals(1, client.activeRouteCountForTest)
            collector2.cancel()
            assertEquals(0, client.activeRouteCountForTest)
        }

    @Test
    fun `awaitResult returns its result after 1000 unrelated frames arrive first`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            val expected = frame(id = 8, type = "result", seq = 0)

            val actual =
                client.awaitResult(commandId = 8) {
                    (1..1000).forEach { index -> client.routeFrame(frame(id = 2, type = "event", seq = index)) }
                    client.routeFrame(expected)
                }

            assertEquals(expected, actual)
            assertEquals(0, client.activeRouteCountForTest)
        }

    @Test
    fun `closing the connection fails waiting collectors instead of leaving them hanging`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = newClient()
            var failure: Throwable? = null
            launch {
                try {
                    client.frames(5).collect { }
                } catch (t: java.io.IOException) {
                    failure = t
                }
            }

            client.closeOpenRoutes()

            assertEquals("connection closed", failure?.message)
            assertEquals(0, client.activeRouteCountForTest)
        }

    private fun frame(
        id: Int,
        type: String,
        seq: Int,
    ): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive(id))
            put("type", JsonPrimitive(type))
            put("seq", JsonPrimitive(seq))
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

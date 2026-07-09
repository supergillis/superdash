package com.superdash.ha

import com.superdash.core.log.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val PING_INTERVAL_MS = 30_000L
private const val PONG_TIMEOUT_MS = 10_000L
private const val COMMAND_RESULT_TIMEOUT_MS = 15_000L
private const val RECENT_EVENTS_CAP = 50
private const val MAX_BACKOFF_MS = 30_000L
// NOTE: ktor-client-websockets is what we use today. Migrating to OkHttp's
// native WebSocket would shave a small dependency surface (OkHttp is already
// pulled in by ktor-client-okhttp) but lose ktor's coroutine-friendly
// session API. Not worth it for the connection count we maintain (1).

private val log = Log("HaWs")

/** Application-scoped HA WebSocket client. Lazy: connect() is idempotent.
 *  Owns its own SupervisorJob scope. Shared across HA-backed features. */
class HaWebSocketClient(
    private val haUrl: StateFlow<String?>,
    private val tokens: HaTokenProvider,
    private val httpClient: HttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = haJson
    private val nextId = AtomicInteger(1)
    private val reconnectSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var loopJob: Job? = null

    // Job refs are written from multiple coroutines (reconnect loop, URL-change
    // observer, the per-connection finally block) and read from disconnect().
    // AtomicReference gives well-defined cross-thread visibility and safe swaps.
    private val pingJobRef = AtomicReference<Job?>(null)

    private val pongs = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    private val runConnectionJobRef = AtomicReference<Job?>(null)
    private val _state = MutableStateFlow<HaConnectionState>(HaConnectionState.Disconnected)
    private val _entities = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    private val _areas = MutableStateFlow<Map<String, HaArea>>(emptyMap())
    private val _entityRegistry = MutableStateFlow<Map<String, HaEntityRegistryEntry>>(emptyMap())
    private val _deviceRegistry = MutableStateFlow<Map<String, HaDeviceRegistryEntry>>(emptyMap())
    private val _voiceExposure =
        MutableStateFlow(HaVoiceExposureSnapshot(exposedEntityIds = emptySet(), loaded = false))

    // ArrayDeque mutated under a small lock; the StateFlow only sees finished
    // snapshots. Avoids the "listOf(line) + it then take(50)" pattern that
    // allocated three intermediate lists per WS frame at HA event rates.
    private val recentEventsBuffer = ArrayDeque<String>(RECENT_EVENTS_CAP)
    private val recentEventsLock = Any()
    private val _recentEvents = MutableStateFlow<List<String>>(emptyList())
    private val _activeSession = MutableStateFlow<WebSocketSession?>(null)
    private val _rawFrames =
        MutableSharedFlow<JsonObject>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val state: StateFlow<HaConnectionState> = _state.asStateFlow()
    val entities: StateFlow<Map<String, EntityState>> = _entities.asStateFlow()
    val areas: StateFlow<Map<String, HaArea>> = _areas.asStateFlow()
    val entityRegistry: StateFlow<Map<String, HaEntityRegistryEntry>> = _entityRegistry.asStateFlow()
    val deviceRegistry: StateFlow<Map<String, HaDeviceRegistryEntry>> = _deviceRegistry.asStateFlow()
    val voiceExposure: StateFlow<HaVoiceExposureSnapshot> = _voiceExposure.asStateFlow()
    val recentEvents: StateFlow<List<String>> = _recentEvents.asStateFlow()
    val rawFrames: SharedFlow<JsonObject> = _rawFrames.asSharedFlow()

    fun nextCommandId(): Int = nextId.getAndIncrement()

    /** Throws if not connected; caller should observe state.value. */
    suspend fun send(payload: JsonObject) {
        val session = _activeSession.value ?: error("HaWs: not connected")
        val text = json.encodeToString(JsonObject.serializer(), payload)
        recordRecent("→ $text")
        session.send(
            io.ktor.websocket.Frame
                .Text(text),
        )
    }

    /** Issue an HA WebSocket command and await the matching `result` frame.
     *
     *  Allocates a fresh command id, frames `{id, type, ...params}`, and waits
     *  for a `result`-typed frame on [rawFrames] with the same id. Subscribe-
     *  before-send is gated via `onSubscription` so a synchronous reply
     *  does not race the collector. `rawFrames` is replay=0 and would
     *  otherwise drop a fast result. Throws if not connected. */
    suspend fun callResult(
        type: String,
        params: JsonObjectBuilder.() -> Unit = {},
    ): JsonObject {
        val commandId = nextCommandId()
        val framed =
            buildJsonObject {
                put("id", JsonPrimitive(commandId))
                put("type", JsonPrimitive(type))
                params()
            }
        return try {
            withTimeout(COMMAND_RESULT_TIMEOUT_MS) {
                rawFrames
                    .onSubscription { send(framed) }
                    .first { frame ->
                        frame["id"]?.jsonPrimitive?.int == commandId &&
                            frame["type"]?.jsonPrimitive?.content == "result"
                    }
            }
        } catch (t: TimeoutCancellationException) {
            throw HaCommandTimeoutException(type, commandId)
        }
    }

    /** Throws if not connected. */
    suspend fun sendBinary(bytes: ByteArray) {
        val session = _activeSession.value ?: error("HaWs: not connected")
        session.send(
            io.ktor.websocket.Frame
                .Binary(true, bytes),
        )
    }

    /** Observes a single HA entity's state. */
    fun observeEntity(entityId: String): Flow<EntityState?> =
        entities.map { it[entityId] }.distinctUntilChanged()

    fun connect() {
        if (loopJob?.isActive == true) {
            return
        }
        loopJob =
            scope.launch {
                launch { observeUrlChanges() }
                reconnectLoop()
            }
    }

    /** Cancels the in-flight connection job when [haUrl] changes so the
     *  reconnect loop picks up the new URL. Cancelling the inner Job (not the
     *  outer loop, and not the WebSocket session directly) keeps cancellation
     *  contained: runConnection's coroutine unwinds, the outer while-loop
     *  iterates, and observeUrlChanges itself never sees a propagated
     *  CancellationException that could kill loopJob. */
    private suspend fun observeUrlChanges() {
        haUrl
            .drop(1)
            .collect { newUrl ->
                log.i("HA URL changed; forcing reconnect", "url" to (newUrl ?: "<null>"))
                runConnectionJobRef.get()?.cancel(UrlChangedCancellation())
                reconnectSignal.tryEmit(Unit)
            }
    }

    fun disconnect() {
        loopJob?.cancel()
        pingJobRef.getAndSet(null)?.cancel()
        _state.value = HaConnectionState.Disconnected
    }

    fun forceReconnect() {
        reconnectSignal.tryEmit(Unit)
    }

    private suspend fun reconnectLoop() {
        var delayMs = 1_000L
        while (true) {
            val url = haUrl.value
            if (url.isNullOrBlank()) {
                _state.value = HaConnectionState.Disconnected
                reconnectSignal.first()
                continue
            }
            _state.value = HaConnectionState.Connecting
            try {
                runConnectionTracked(url)
                delayMs = 1_000L
            } catch (t: NotAuthenticatedExceptionWrapper) {
                log.w("not authenticated; awaiting reconnect signal")
                _state.value = HaConnectionState.NeedsReauth("not authenticated")
                // Wait for an external trigger (token change, URL change, network
                // availability, manual force-reconnect) before retrying. Don't return
                // permanently. Transient HA-side states (config reload, brief
                // auth_invalid during restart) shouldn't cement into permanent broken.
                reconnectSignal.first()
                continue
            } catch (t: AuthInvalidException) {
                log.w("HA returned auth_invalid; awaiting reconnect signal")
                _state.value = HaConnectionState.NeedsReauth(t.message ?: "auth invalid")
                reconnectSignal.first()
                continue
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    // A URL change cancels the in-flight connection job but must not
                    // tear down the outer reconnect loop. Other CancellationException
                    // values come from a real disconnect() and must propagate.
                    if (isUrlChangedCancellation(t)) {
                        log.i("connection cancelled for URL change")
                        continue
                    }
                    throw t
                }
                log.w("connection failed", t)
                _state.value = HaConnectionState.Failed(t.message ?: "unknown")
            }
            withTimeoutOrNull(delayMs) { reconnectSignal.first() }
            delayMs = (delayMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** Run [runConnection] in a job we can cancel from [observeUrlChanges]
     *  without unwinding the outer reconnect loop. Exceptions thrown by
     *  [runConnection] propagate to the caller via [coroutineScope]. */
    private suspend fun runConnectionTracked(url: String) {
        coroutineScope {
            val job =
                launch {
                    runConnection(url)
                }
            runConnectionJobRef.set(job)
            try {
                job.join()
            } finally {
                runConnectionJobRef.compareAndSet(job, null)
            }
        }
    }

    private fun isUrlChangedCancellation(t: CancellationException): Boolean =
        t is UrlChangedCancellation || t.cause is UrlChangedCancellation

    private suspend fun runConnection(baseUrl: String) {
        val wsUrl = "${baseUrl.replace("http://", "ws://").replace("https://", "wss://")}/api/websocket"
        httpClient.webSocket({ url(wsUrl) }) {
            handshake()
            seedStates()
            seedAreas()
            seedEntityRegistry()
            seedDeviceRegistry()
            seedVoiceExposure()
            subscribeStateChanges()
            _activeSession.value = this
            val session = this
            try {
                // Launch the ping loop on the per-connection scope so that ending
                // the connection (session close, URL change, disconnect()) ends
                // the loop via structured concurrency. Launching on the
                // class-level scope would let it outlive its session.
                coroutineScope {
                    val pingJob =
                        launch {
                            runPingLoop(
                                // The ping is sent from awaitPong's onSubscription so the
                                // pong collector is attached before the ping goes out. Sending
                                // in sendPing (before subscribing) lets a fast pong land while
                                // pongs has replay=0 and no collector, causing a spurious
                                // timeout/disconnect. Mirrors runPingLoopForTest.
                                sendPing = { /* sent via onSubscription below */ },
                                awaitPong = { pingId ->
                                    pongs
                                        .onSubscription { session.sendCommand(PingCommand(pingId)) }
                                        .first { it == pingId }
                                },
                                onTimeout = { pingId ->
                                    log.w("ping timed out; closing websocket", null, "pingId" to pingId)
                                    session.cancel(CancellationException("HA websocket ping timed out"))
                                },
                            )
                        }
                    pingJobRef.set(pingJob)
                    try {
                        receiveLoop()
                    } finally {
                        pingJobRef.compareAndSet(pingJob, null)
                        pingJob.cancel()
                    }
                }
            } finally {
                _activeSession.value = null
            }
        }
    }

    private suspend fun WebSocketSession.handshake() {
        // TODO: should we move to some kind of actor model?
        // What if we send multiple commands and the readNextFrame returns response for another command?

        val authReq =
            readNextFrame() as? AuthRequired
                ?: error("expected auth_required as first frame")
        log.i("auth requested", "haVersion" to authReq.haVersion)
        val token = acquireTokenForConnect()
        sendCommand(AuthCommand(token))
        when (val response = readNextFrame()) {
            is AuthOk -> {
                log.i("auth ok", "haVersion" to response.haVersion)
                _state.value = HaConnectionState.Connected(response.haVersion)
            }
            is AuthInvalid -> throw AuthInvalidException(response.message)
            else -> error("unexpected post-auth frame: " + response::class.simpleName)
        }
    }

    /** Fetch an access token for the auth handshake, classifying failures.
     *  Internal so the reconnect-latch behaviour can be unit-tested without a
     *  live websocket. */
    internal suspend fun acquireTokenForConnect(): String =
        try {
            tokens.get()
        } catch (t: CancellationException) {
            throw t
        } catch (t: NotAuthenticatedException) {
            // Only a genuine "no stored credentials" is a reauth condition.
            // Wrap it so the loop parks awaiting a token change instead of
            // retrying. Any other failure (transient IO during refresh, HA
            // restarting, a LAN blip) falls through and propagates so the
            // reconnect loop retries it with the existing exponential backoff
            // rather than latching the socket permanently dead.
            throw NotAuthenticatedExceptionWrapper(t)
        }

    private suspend fun WebSocketSession.seedStates() {
        val id = nextId.getAndIncrement()
        sendCommand(GetStatesCommand(id))

        val response = readNextFrame() as? ResultFrame ?: error("expected result for get_states")
        if (!response.success) {
            error("get_states failed: " + response.error)
        }
        val map =
            response
                .requireResultList<EntityState>("get_states")
                .associateBy { entity -> entity.entityId }
        _entities.value = map
        log.i("seeded entities", "count" to map.size)
    }

    private suspend fun WebSocketSession.seedAreas() {
        val id = nextId.getAndIncrement()
        try {
            sendCommand(ListAreasCommand(id))
            val response = readNextFrame() as? ResultFrame ?: error("expected result for area registry")
            if (!response.success) {
                log.w("area registry failed", null, "error" to response.error.toString())
                return
            }
            val map =
                response
                    .requireResultList<HaArea>("area registry")
                    .associateBy { area -> area.areaId }
            _areas.value = map
            log.i("seeded areas", "count" to map.size)
        } catch (t: Throwable) {
            if (t is CancellationException) {
                throw t
            }
            log.w("failed to seed areas", t)
        }
    }

    private suspend fun WebSocketSession.seedEntityRegistry() {
        val id = nextId.getAndIncrement()
        try {
            sendCommand(ListEntityRegistryCommand(id))
            val response = readNextFrame() as? ResultFrame ?: error("expected result for entity registry")
            if (!response.success) {
                log.w("entity registry failed", null, "error" to response.error.toString())
                return
            }
            val map =
                response
                    .requireResultList<HaEntityRegistryEntry>("entity registry")
                    .filter { entry -> entry.disabledBy == null && entry.hiddenBy == null }
                    .associateBy { entry -> entry.entityId }
            _entityRegistry.value = map
            log.i("seeded entity registry", "count" to map.size)
        } catch (t: Throwable) {
            if (t is CancellationException) {
                throw t
            }
            log.w("failed to seed entity registry", t)
        }
    }

    private suspend fun WebSocketSession.seedDeviceRegistry() {
        val id = nextId.getAndIncrement()
        try {
            sendCommand(ListDeviceRegistryCommand(id))
            val response = readNextFrame() as? ResultFrame ?: error("expected result for device registry")
            if (!response.success) {
                log.w("device registry failed", null, "error" to response.error.toString())
                return
            }
            val map =
                response
                    .requireResultList<HaDeviceRegistryEntry>("device registry")
                    .associateBy { entry -> entry.deviceId }
            _deviceRegistry.value = map
            log.i("seeded device registry", "count" to map.size)
        } catch (t: Throwable) {
            if (t is CancellationException) {
                throw t
            }
            log.w("failed to seed device registry", t)
        }
    }

    private suspend fun WebSocketSession.seedVoiceExposure() {
        val id = nextId.getAndIncrement()
        try {
            sendCommand(ListVoiceExposedEntitiesCommand(id))
            val response = readNextFrame() as? ResultFrame ?: error("expected result for voice exposure")
            if (!response.success) {
                val exposure = entityRegistryExposureFallback()
                _voiceExposure.value = exposure
                log.w("voice exposure failed", null, "error" to response.error.toString())
                return
            }
            val result = response.result as? JsonObject ?: error("expected result object for voice exposure")
            val exposure =
                extractConversationExposure(result).let { parsedExposure ->
                    if (parsedExposure.loaded) {
                        parsedExposure
                    } else {
                        entityRegistryExposureFallback()
                    }
                }
            _voiceExposure.value = exposure
            log.i("seeded voice exposure", "count" to exposure.exposedEntityIds.size, "loaded" to exposure.loaded)
        } catch (t: Throwable) {
            if (t is CancellationException) {
                throw t
            }
            _voiceExposure.value = entityRegistryExposureFallback()
            log.w("failed to seed voice exposure", t)
        }
    }

    private fun entityRegistryExposureFallback(): HaVoiceExposureSnapshot =
        extractConversationExposureFromEntityRegistry(_entityRegistry.value.values)

    private suspend fun WebSocketSession.subscribeStateChanges() {
        val id = nextId.getAndIncrement()
        sendCommand(SubscribeEventsCommand(id, "state_changed"))

        val response = readNextFrame() as? ResultFrame ?: error("expected result for subscribe_events")
        if (!response.success) {
            error("subscribe_events failed: " + response.error)
        }
        log.i("subscribed to state_changed", "subscriptionId" to id)
    }

    /** Drives the websocket keepalive. Pure suspend: the caller owns the
     *  coroutine, so the loop ends when its enclosing scope is cancelled. */
    internal suspend fun runPingLoop(
        sendPing: suspend (pingId: Int) -> Unit,
        awaitPong: suspend (pingId: Int) -> Unit,
        onTimeout: (pingId: Int) -> Unit,
        pingIntervalMs: Long = PING_INTERVAL_MS,
        pongTimeoutMs: Long = PONG_TIMEOUT_MS,
    ) {
        while (true) {
            delay(pingIntervalMs)
            val pingId = nextId.getAndIncrement()
            sendPing(pingId)
            val gotPong =
                withTimeoutOrNull(pongTimeoutMs) {
                    awaitPong(pingId)
                }
            if (gotPong == null) {
                onTimeout(pingId)
                break
            }
        }
    }

    private suspend fun WebSocketSession.receiveLoop() {
        for (frame in incoming) {
            if (frame !is Frame.Text) {
                continue
            }
            val text = frame.readText()
            recordRecent("← $text")
            val rawObj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (rawObj != null) {
                _rawFrames.emit(rawObj)
            }
            val parsed =
                runCatching { json.decodeFromString<HaWsFrame>(text) }.getOrNull()
                    ?: continue
            when (parsed) {
                is EventFrame ->
                    // A single malformed event (e.g. a state payload we can't decode)
                    // must not tear down the whole connection. Log and skip it.
                    runCatching { _entities.update { applyEntityEvent(it, parsed.event) } }
                        .onFailure { t -> log.w("failed to apply state_changed event; skipping", t) }
                is Pong -> pongs.tryEmit(parsed.id)
                else -> { /* ignored */ }
            }
        }
    }

    private suspend fun WebSocketSession.readNextFrame(): HaWsFrame {
        for (frame in incoming) {
            if (frame !is Frame.Text) {
                continue
            }
            val text = frame.readText()
            recordRecent("← $text")
            return json.decodeFromString(text)
        }
        error("connection closed before frame received")
    }

    private suspend fun WebSocketSession.sendCommand(cmd: HaCommand) {
        val text = json.encodeToString(cmd)
        recordRecent("→ $text")
        send(Frame.Text(text))
    }

    private fun recordRecent(line: String) {
        val redacted = redactSecrets(line)
        val snapshot =
            synchronized(recentEventsLock) {
                recentEventsBuffer.addFirst(redacted)
                while (recentEventsBuffer.size > RECENT_EVENTS_CAP) {
                    recentEventsBuffer.removeLast()
                }
                recentEventsBuffer.toList()
            }
        _recentEvents.value = snapshot
    }

    internal val scopeForTest: CoroutineScope get() = scope

    private companion object {
        // Mask the access token in any frame we record for the debug screen so the
        // long-lived HA token never shows up in recentEvents. The outbound auth
        // frame is the obvious source, but redacting by field name catches the
        // token wherever it appears regardless of direction. The value pattern
        // consumes JSON escapes (\" \\ …) so a token containing an escaped quote
        // is fully masked instead of leaking its tail.
        private val ACCESS_TOKEN_REGEX = Regex("(\"access_token\"\\s*:\\s*\")(?:\\\\.|[^\"\\\\])*\"")

        fun redactSecrets(line: String): String =
            ACCESS_TOKEN_REGEX.replace(line) { match -> "${match.groupValues[1]}<redacted>\"" }
    }

    internal val isAnyJobRefActiveForTest: Boolean
        get() = pingJobRef.get()?.isActive == true || runConnectionJobRef.get()?.isActive == true

    /** Test-only wrapper around [runPingLoop] using test-scaled timing.
     *  [respond] is invoked while the loop is suspended in [awaitPong], so
     *  tests can choose to ack or to withhold the pong. */
    internal suspend fun runPingLoopForTest(
        sendPing: suspend (pingId: Int) -> Unit,
        respond: suspend (pingId: Int) -> Unit,
        onTimeout: (pingId: Int) -> Unit,
        pingIntervalMs: Long = 100L,
        pongTimeoutMs: Long = 50L,
    ) {
        runPingLoop(
            sendPing = { pingId -> sendPing(pingId) },
            awaitPong = { pingId ->
                pongs
                    .onSubscription { respond(pingId) }
                    .first { it == pingId }
            },
            onTimeout = onTimeout,
            pingIntervalMs = pingIntervalMs,
            pongTimeoutMs = pongTimeoutMs,
        )
    }

    internal fun deliverPongForTest(pingId: Int) {
        pongs.tryEmit(pingId)
    }

    internal fun redactSecretsForTest(line: String): String = redactSecrets(line)

    internal class NotAuthenticatedExceptionWrapper(
        cause: Throwable,
    ) : Exception(cause)

    /** Internal cancellation reason used to distinguish a URL-driven cancel
     *  (loop continues) from disconnect()/scope cancel (loop tears down). */
    private class UrlChangedCancellation : CancellationException("HA URL changed")
}

class AuthInvalidException(
    message: String,
) : Exception(message)

class HaCommandTimeoutException(
    val commandType: String,
    val commandId: Int,
) : Exception("timed out waiting for HA result for $commandType id=$commandId")

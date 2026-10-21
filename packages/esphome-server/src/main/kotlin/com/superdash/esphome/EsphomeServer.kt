package com.superdash.esphome

import com.superdash.core.log.Log
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readByte
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout

private val log = Log("EsphomeServer")

private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L
private const val HEALTHY_RESET_MS = 30_000L

internal class EsphomeServer(
    private val scope: CoroutineScope,
    private val enabled: Flow<Boolean>,
    private val deviceInfo: EsphomeDeviceInfo,
    private val entities: () -> List<EsphomeEntity>,
    private val noiseConfig: Flow<EsphomeNoiseConfig>,
    private val mdnsFactory: (noiseEnabled: Boolean) -> EsphomeMdns,
    private val port: Int = 6053,
    private val selectorFactory: () -> SelectorManager = { SelectorManager(Dispatchers.IO) },
) {
    private var supervisor: Job? = null

    /** A null listener config means the server is stopped. If the noise config or
     *  the enabled flow fails, the server stays stopped until the app restarts,
     *  rather than falling back to plaintext. */
    fun start() {
        supervisor?.cancel()
        supervisor =
            scope.launch {
                combine(enabled, noiseConfig) { isEnabled, config -> config.takeIf { isEnabled } }
                    .catch {
                        log.e("listener config failed; server stays stopped", it)
                        emit(null)
                    }.distinctUntilChanged()
                    .collectLatest { config ->
                        if (config == null) {
                            log.i("stopped")
                            return@collectLatest
                        }
                        runWithRestart { runServer(config) }
                    }
            }
    }

    /** Cancelling the surrounding `collectLatest` block cancels [supervisorScope]
     *  and every client connection in it. A client failure does not stop the accept
     *  loop, while a bind or mDNS failure propagates to [runWithRestart]. The mDNS
     *  advertisement lives exactly as long as the listener. */
    internal suspend fun runServer(config: EsphomeNoiseConfig) {
        selectorFactory().use { selector ->
            aSocket(selector).tcp().bind(port = port).use { server ->
                val mdns = mdnsFactory(config is EsphomeNoiseConfig.NoiseOnly)
                try {
                    mdns.start()
                    log.i("listening", "port" to port)
                    supervisorScope {
                        while (true) {
                            val socket = server.accept()
                            launch { serveClient(socket, config) }
                        }
                    }
                } finally {
                    mdns.stop()
                }
            }
        }
    }

    private suspend fun serveClient(
        socket: Socket,
        config: EsphomeNoiseConfig,
    ) {
        log.i("client accepted", "remote" to socket.remoteAddress.toString())
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = true)
        try {
            val transport =
                withTimeout(DEFAULT_IDLE_TIMEOUT_MS) {
                    buildTransport(input, output, config, deviceInfo)
                }
            if (transport == null) {
                log.w("rejecting client: preamble does not match active mode")
                return
            }
            EsphomeConnection(
                transport = transport,
                deviceInfo = deviceInfo,
                entities = entities(),
            ).run()
        } catch (timeout: TimeoutCancellationException) {
            log.w("client setup timed out", null, "afterMs" to DEFAULT_IDLE_TIMEOUT_MS)
        } catch (t: Throwable) {
            if (isExpectedDisconnect(t)) {
                log.i("client disconnected")
            } else {
                log.w("client setup failed", t)
            }
        } finally {
            runCatching { socket.close() }
            runCatching { output.close() }
        }
    }
}

private suspend fun buildTransport(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    config: EsphomeNoiseConfig,
    deviceInfo: EsphomeDeviceInfo,
): EsphomeTransport? {
    val preamble = input.readByte().toInt() and 0xFF
    return when (config) {
        is EsphomeNoiseConfig.PlainOnly ->
            if (preamble == 0x00) {
                PlainTransport(input = input, output = output, firstFrameConsumesPreamble = true)
            } else {
                null
            }
        is EsphomeNoiseConfig.NoiseOnly ->
            if (preamble == NOISE_PREAMBLE) {
                val handshake =
                    performNoiseHandshake(
                        input = input,
                        output = output,
                        psk = config.psk,
                        serverName = deviceInfo.name,
                        macAddress = deviceInfo.macAddress,
                    )
                NoiseTransport(
                    input = input,
                    output = output,
                    sendCipher = handshake.sendCipher,
                    recvCipher = handshake.recvCipher,
                )
            } else {
                null
            }
    }
}

/** Run [block] in a restart loop. Failures are caught and retried with
 *  exponential backoff (doubling from [initialBackoffMs] up to [maxBackoffMs]).
 *  If [block] ran for at least [healthyResetMs] before failing, the backoff
 *  resets to [initialBackoffMs] on the next failure. `CancellationException`
 *  is rethrown without retrying so that toggle-off and scope cancellation
 *  propagate normally. A clean return from [block] exits the loop.
 *
 *  [clock] returns monotonic milliseconds; the default uses `System.nanoTime`.
 *  Tests inject `{ currentTime }` to drive the loop with virtual time. */
internal suspend fun runWithRestart(
    initialBackoffMs: Long = INITIAL_BACKOFF_MS,
    maxBackoffMs: Long = MAX_BACKOFF_MS,
    healthyResetMs: Long = HEALTHY_RESET_MS,
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    block: suspend () -> Unit,
) {
    var delayMs = initialBackoffMs
    while (true) {
        val startedAt = clock()
        try {
            block()
            return
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            val uptimeMs = clock() - startedAt
            log.w("listener died; retrying", t, "uptimeMs" to uptimeMs, "delayMs" to delayMs)
            if (uptimeMs >= healthyResetMs) {
                delayMs = initialBackoffMs
            }
            kotlinx.coroutines.delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(maxBackoffMs)
        }
    }
}

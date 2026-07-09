package com.superdash.esphome

import com.superdash.core.log.Log
import io.ktor.network.selector.SelectorManager
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    private val noiseConfig: () -> EsphomeNoiseConfig,
    private val mdns: EsphomeMdns,
    private val port: Int = 6053,
) {
    private var supervisor: Job? = null

    @Volatile private var mdnsHolder: EsphomeMdns = mdns

    @Volatile private var mdnsActive: Boolean = false

    fun swapMdns(newMdns: EsphomeMdns) {
        val old = mdnsHolder
        mdnsHolder = newMdns
        if (mdnsActive) {
            old.stop()
            newMdns.start()
        }
    }

    fun start() {
        supervisor?.cancel()
        supervisor =
            scope.launch {
                enabled.collectLatest { isEnabled ->
                    if (!isEnabled) {
                        mdnsHolder.stop()
                        mdnsActive = false
                        log.i("disabled")
                        return@collectLatest
                    }
                    runWithRestart { runServer() }
                }
            }
    }

    fun stop() {
        supervisor?.cancel()
        supervisor = null
        mdnsHolder.stop()
        mdnsActive = false
    }

    /** All client-connection coroutines run in this enclosing `supervisorScope`,
     *  so toggling `enabled` to false cancels the `collectLatest` block, which
     *  cancels this whole scope, which propagates cancellation to every active
     *  client connection. Without the wrapping scope, client launches would
     *  inherit the outer application scope and outlive the toggle. We use
     *  `supervisorScope` rather than `coroutineScope` so that an exception
     *  thrown by one client connection does not cancel the accept loop or
     *  sibling connections; whole-listener failures (bind, mDNS) still
     *  propagate out to [runWithRestart] in [start]. */
    private suspend fun runServer() =
        kotlinx.coroutines.supervisorScope {
            val selector = SelectorManager(Dispatchers.IO)
            val server = aSocket(selector).tcp().bind(port = port)
            mdnsHolder.start()
            mdnsActive = true
            log.i("listening", "port" to port)
            try {
                while (true) {
                    val socket =
                        try {
                            server.accept()
                        } catch (throwable: Throwable) {
                            throw throwable
                        }
                    launch {
                        log.i("client accepted", "remote" to socket.remoteAddress.toString())
                        val input = socket.openReadChannel()
                        val output = socket.openWriteChannel(autoFlush = true)
                        try {
                            val transport =
                                withTimeout(DEFAULT_IDLE_TIMEOUT_MS) {
                                    buildTransport(input, output, noiseConfig(), deviceInfo)
                                }
                            if (transport == null) {
                                log.w("rejecting client: preamble does not match active mode")
                                return@launch
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
            } finally {
                runCatching { server.close() }
                selector.close()
                mdnsHolder.stop()
                mdnsActive = false
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

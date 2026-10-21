package com.superdash.esphome

import android.content.Context
import io.ktor.network.selector.SelectorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.net.BindException
import java.net.ServerSocket

internal val testDeviceInfo =
    EsphomeDeviceInfo(
        name = "test-device",
        macAddress = "AA:BB:CC:DD:EE:FF",
        esphomeVersion = "2026.4.5",
        compilationTime = "",
        model = "Test",
        manufacturer = "Acme",
        friendlyName = "Test Device",
    )

private const val EVENT_TIMEOUT_MS = 5_000L

internal fun newEventLog(): Channel<String> = Channel(Channel.UNLIMITED)

/** Uses real time so it also works under a test scheduler, where virtual time would skip the timeout. */
internal suspend fun Channel<String>.next(): String =
    withContext(Dispatchers.Default) { withTimeout(EVENT_TIMEOUT_MS) { receive() } }

/** Waits for [port] to be bindable again. Ktor closes listeners asynchronously, so one bind attempt can be early. */
internal suspend fun awaitPortFree(port: Int) {
    withTimeout(EVENT_TIMEOUT_MS) {
        while (true) {
            try {
                ServerSocket(port).close()
                return@withTimeout
            } catch (_: BindException) {
                yield()
            }
        }
    }
}

internal fun Channel<String>.drain(): List<String> {
    val events = mutableListOf<String>()
    while (true) {
        events += tryReceive().getOrNull() ?: return events
    }
}

/** Records `start:<noiseEnabled>` and `stop:<noiseEnabled>` in [events]. [onStop] runs after the event is recorded. */
internal class FakeMdns(
    context: Context,
    private val noise: Boolean,
    private val events: Channel<String>,
    private val failOnStart: Boolean = false,
    private val onStop: () -> Unit = {},
) : EsphomeMdns(context, testDeviceInfo, noiseEnabled = noise) {
    override fun start() {
        events.trySend("start:$noise")
        if (failOnStart) {
            error("mdns start failed")
        }
    }

    override fun stop() {
        events.trySend("stop:$noise")
        onStop()
    }
}

internal class TrackingSelector(
    private val delegate: SelectorManager = SelectorManager(kotlinx.coroutines.Dispatchers.IO),
) : SelectorManager by delegate {
    @Volatile var closed = false
        private set

    override fun close() {
        closed = true
        delegate.close()
    }
}

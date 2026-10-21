package com.superdash.esphome

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket

@OptIn(ExperimentalCoroutinesApi::class)
class EsphomeServerMdnsTest {
    private val context: Context = android.app.Application()

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private data class StartupFailureCase(
        val name: String,
        val portHeld: Boolean,
        val mdnsFails: Boolean,
        val expectedEvents: List<String>,
    )

    @Test
    fun `runServer closes the selector and frees the port when startup fails`() =
        runBlocking {
            val cases =
                listOf(
                    StartupFailureCase("bind fails", portHeld = true, mdnsFails = false, expectedEvents = emptyList()),
                    StartupFailureCase(
                        "mdns start fails",
                        portHeld = false,
                        mdnsFails = true,
                        expectedEvents = listOf("start:false", "stop:false"),
                    ),
                )
            for (case in cases) {
                val port = freePort()
                val holder = if (case.portHeld) ServerSocket(port) else null
                val events = newEventLog()
                val selector = TrackingSelector()
                val server =
                    EsphomeServer(
                        scope = CoroutineScope(Job()),
                        enabled = flowOf(false),
                        deviceInfo = testDeviceInfo,
                        entities = { emptyList() },
                        noiseConfig = flowOf(EsphomeNoiseConfig.PlainOnly),
                        mdnsFactory = { noise -> FakeMdns(context, noise, events, failOnStart = case.mdnsFails) },
                        port = port,
                        selectorFactory = { selector },
                    )

                val failure = runCatching { server.runServer(EsphomeNoiseConfig.PlainOnly) }.exceptionOrNull()
                holder?.close()

                assertNotNull(case.name, failure)
                assertEquals(case.name, true, selector.closed)
                assertEquals(case.name, case.expectedEvents, events.drain())
                awaitPortFree(port)
            }
        }

    private val noisy = EsphomeNoiseConfig.NoiseOnly(ByteArray(32) { it.toByte() })

    private fun runningServer(
        enabled: MutableStateFlow<Boolean>,
        noiseConfig: Flow<EsphomeNoiseConfig>,
        events: Channel<String>,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        onStop: () -> Unit = {},
    ): Job {
        val job = Job()
        val server =
            EsphomeServer(
                scope = CoroutineScope(job + dispatcher),
                enabled = enabled,
                deviceInfo = testDeviceInfo,
                entities = { emptyList() },
                noiseConfig = noiseConfig,
                mdnsFactory = { noise -> FakeMdns(context, noise, events, onStop = onStop) },
                port = freePort(),
            )
        server.start()
        return job
    }

    @Test
    fun `a config change restarts the listener and the old mdns stops before the new one starts`() =
        runBlocking {
            val events = newEventLog()
            val config = MutableStateFlow<EsphomeNoiseConfig>(EsphomeNoiseConfig.PlainOnly)
            val job = runningServer(MutableStateFlow(true), config, events)
            assertEquals("start:false", events.next())

            config.value = noisy
            assertEquals("stop:false", events.next())
            assertEquals("start:true", events.next())
            job.cancelAndJoin()

            assertEquals(listOf("stop:true"), events.drain())
        }

    @Test
    fun `a config change racing a disable leaves nothing advertising`() =
        runTest {
            val events = newEventLog()
            val enabled = MutableStateFlow(true)
            val config = MutableStateFlow<EsphomeNoiseConfig>(EsphomeNoiseConfig.PlainOnly)
            val job =
                runningServer(
                    enabled,
                    config,
                    events,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    onStop = { config.value = noisy },
                )
            runCurrent()
            assertEquals("start:false", events.next())

            enabled.value = false
            runCurrent()
            assertEquals(listOf("stop:false"), events.drain())

            enabled.value = true
            runCurrent()
            assertEquals("start:true", events.next())
            job.cancelAndJoin()

            assertEquals(listOf("stop:true"), events.drain())
        }

    @Test
    fun `a config change while disabled does not advertise`() =
        runTest {
            val events = newEventLog()
            val enabled = MutableStateFlow(false)
            val config = MutableStateFlow<EsphomeNoiseConfig>(EsphomeNoiseConfig.PlainOnly)
            val job = runningServer(enabled, config, events, dispatcher = StandardTestDispatcher(testScheduler))
            runCurrent()

            config.value = noisy
            runCurrent()
            assertEquals(emptyList<String>(), events.drain())

            enabled.value = true
            runCurrent()
            assertEquals("start:true", events.next())
            job.cancelAndJoin()

            assertEquals(listOf("stop:true"), events.drain())
        }

    @Test
    fun `a noise config error stops the server instead of falling back to plaintext`() =
        runBlocking {
            val events = newEventLog()
            val failNow = CompletableDeferred<Unit>()
            val failingConfig =
                flow {
                    emit(EsphomeNoiseConfig.PlainOnly)
                    failNow.await()
                    throw IOException("psk store failed")
                }
            val job = runningServer(MutableStateFlow(true), failingConfig, events)
            assertEquals("start:false", events.next())

            failNow.complete(Unit)
            job.children.forEach { it.join() }

            assertEquals(listOf("stop:false"), events.drain())
            job.cancelAndJoin()
        }
}

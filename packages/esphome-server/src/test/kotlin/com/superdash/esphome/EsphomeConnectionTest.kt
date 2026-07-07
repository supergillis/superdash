package com.superdash.esphome

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.esphome.api.HelloRequest
import org.esphome.api.HelloResponse
import org.esphome.api.ListEntitiesNumberResponse
import org.esphome.api.ListEntitiesSelectResponse
import org.esphome.api.ListEntitiesSensorResponse
import org.esphome.api.ListEntitiesTextSensorResponse
import org.esphome.api.NumberStateResponse
import org.esphome.api.PingRequest
import org.esphome.api.PingResponse
import org.esphome.api.SelectStateResponse
import org.esphome.api.SensorStateResponse
import org.esphome.api.TextSensorStateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EsphomeConnectionTest {
    private val deviceInfo =
        EsphomeDeviceInfo(
            name = "superdash-test",
            macAddress = "AA:BB:CC:DD:EE:FF",
            esphomeVersion = "2026.4.5-superdash",
            compilationTime = "",
            model = "Pixel emulator",
            manufacturer = "Google",
            friendlyName = "Superdash Test",
        )

    @Test
    fun `responds to HelloRequest with HelloResponse`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)

            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = emptyList(),
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest
                    .newBuilder()
                    .setClientInfo("test-client")
                    .build()
                    .toByteArray(),
            )
            val helloResp = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.HELLO_RESPONSE, helloResp.messageType)
            val helloParsed = HelloResponse.parseFrom(helloResp.payload)
            assertEquals(1, helloParsed.apiVersionMajor)
            assertEquals(14, helloParsed.apiVersionMinor)

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `ListEntitiesRequest emits one Switch + one BinarySensor + Done`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)

            val keepScreenOnState = MutableStateFlow(true)
            val screenOnState = MutableStateFlow(false)
            val entities =
                listOf(
                    EsphomeEntity.Switch(
                        key = keyFromObjectId("keep_screen_on"),
                        objectId = "keep_screen_on",
                        name = "Keep Screen On",
                        state = keepScreenOnState,
                        onCommand = { keepScreenOnState.value = it },
                    ),
                    EsphomeEntity.BinarySensor(
                        key = keyFromObjectId("screen_on"),
                        objectId = "screen_on",
                        name = "Screen On",
                        state = screenOnState,
                    ),
                )

            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            // Send hello to satisfy the auth-state gate before listing.
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest.newBuilder().build().toByteArray(),
            )
            serverToClient.readEsphomeFrame() // HelloResponse

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.LIST_ENTITIES_REQUEST,
                org.esphome.api.ListEntitiesRequest
                    .newBuilder()
                    .build()
                    .toByteArray(),
            )

            val first = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_SWITCH_RESPONSE, first.messageType)
            val second = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_BINARY_SENSOR_RESPONSE, second.messageType)
            val done = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_DONE_RESPONSE, done.messageType)

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `ListEntitiesRequest emits sensor text sensor number select and done`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)
            val sensorKey = keyFromObjectId("ha_entity_count")
            val textSensorKey = keyFromObjectId("ha_connection_state")
            val numberKey = keyFromObjectId("idle_timeout_sec")
            val selectKey = keyFromObjectId("day_screensaver_mode")
            val selectOptions = listOf("off", "photos", "immich", "media_library", "clock", "black")
            val entities =
                listOf(
                    EsphomeEntity.Sensor(
                        key = sensorKey,
                        objectId = "ha_entity_count",
                        name = "HA Entity Count",
                        state = MutableStateFlow(2f),
                        unitOfMeasurement = "items",
                        accuracyDecimals = 1,
                        deviceClass = "data_size",
                    ),
                    EsphomeEntity.TextSensor(
                        key = textSensorKey,
                        objectId = "ha_connection_state",
                        name = "HA Connection State",
                        state = MutableStateFlow("Connected"),
                        deviceClass = "enum",
                    ),
                    EsphomeEntity.Number(
                        key = numberKey,
                        objectId = "idle_timeout_sec",
                        name = "Idle Timeout",
                        state = MutableStateFlow(300f),
                        minValue = 0f,
                        maxValue = 1800f,
                        step = 30f,
                        unitOfMeasurement = "s",
                        onCommand = {},
                    ),
                    EsphomeEntity.Select(
                        key = selectKey,
                        objectId = "day_screensaver_mode",
                        name = "Day Screensaver Mode",
                        state = MutableStateFlow("photos"),
                        options = selectOptions,
                        onCommand = {},
                    ),
                )
            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest.newBuilder().build().toByteArray(),
            )
            serverToClient.readEsphomeFrame()
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.LIST_ENTITIES_REQUEST,
                org.esphome.api.ListEntitiesRequest
                    .newBuilder()
                    .build()
                    .toByteArray(),
            )

            val sensorFrame = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_SENSOR_RESPONSE, sensorFrame.messageType)
            val sensorResponse = ListEntitiesSensorResponse.parseFrom(sensorFrame.payload)
            assertEquals(sensorKey, sensorResponse.key)
            assertEquals("ha_entity_count", sensorResponse.objectId)
            assertEquals("HA Entity Count", sensorResponse.name)
            assertEquals("items", sensorResponse.unitOfMeasurement)
            assertEquals(1, sensorResponse.accuracyDecimals)
            assertEquals("data_size", sensorResponse.deviceClass)

            val textSensorFrame = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_TEXT_SENSOR_RESPONSE, textSensorFrame.messageType)
            val textSensorResponse = ListEntitiesTextSensorResponse.parseFrom(textSensorFrame.payload)
            assertEquals(textSensorKey, textSensorResponse.key)
            assertEquals("ha_connection_state", textSensorResponse.objectId)
            assertEquals("HA Connection State", textSensorResponse.name)
            assertEquals("enum", textSensorResponse.deviceClass)

            val numberFrame = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_NUMBER_RESPONSE, numberFrame.messageType)
            val numberResponse = ListEntitiesNumberResponse.parseFrom(numberFrame.payload)
            assertEquals(numberKey, numberResponse.key)
            assertEquals("idle_timeout_sec", numberResponse.objectId)
            assertEquals("Idle Timeout", numberResponse.name)
            assertEquals(0f, numberResponse.minValue)
            assertEquals(1800f, numberResponse.maxValue)
            assertEquals(30f, numberResponse.step)
            assertEquals("s", numberResponse.unitOfMeasurement)

            val selectFrame = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_SELECT_RESPONSE, selectFrame.messageType)
            val selectResponse = ListEntitiesSelectResponse.parseFrom(selectFrame.payload)
            assertEquals(selectKey, selectResponse.key)
            assertEquals("day_screensaver_mode", selectResponse.objectId)
            assertEquals("Day Screensaver Mode", selectResponse.name)
            assertEquals(selectOptions, selectResponse.optionsList)

            assertEquals(
                EsphomeMessageType.LIST_ENTITIES_DONE_RESPONSE,
                serverToClient.readEsphomeFrame().messageType,
            )

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `SubscribeStates pushes initial state for both entities`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)

            val keepScreenOnState = MutableStateFlow(true)
            val screenOnState = MutableStateFlow(false)
            val entities =
                listOf(
                    EsphomeEntity.Switch(
                        key = keyFromObjectId("keep_screen_on"),
                        objectId = "keep_screen_on",
                        name = "Keep Screen On",
                        state = keepScreenOnState,
                        onCommand = { keepScreenOnState.value = it },
                    ),
                    EsphomeEntity.BinarySensor(
                        key = keyFromObjectId("screen_on"),
                        objectId = "screen_on",
                        name = "Screen On",
                        state = screenOnState,
                    ),
                )

            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(EsphomeMessageType.HELLO_REQUEST, HelloRequest.newBuilder().build().toByteArray())
            serverToClient.readEsphomeFrame()
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.SUBSCRIBE_STATES_REQUEST,
                org.esphome.api.SubscribeStatesRequest
                    .newBuilder()
                    .build()
                    .toByteArray(),
            )

            val firstState = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.SWITCH_STATE_RESPONSE, firstState.messageType)
            val parsedSwitch =
                org.esphome.api.SwitchStateResponse
                    .parseFrom(firstState.payload)
            assertEquals(keyFromObjectId("keep_screen_on"), parsedSwitch.key)
            assertEquals(true, parsedSwitch.state)

            val secondState = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.BINARY_SENSOR_STATE_RESPONSE, secondState.messageType)
            val parsedBinary =
                org.esphome.api.BinarySensorStateResponse
                    .parseFrom(secondState.payload)
            assertEquals(keyFromObjectId("screen_on"), parsedBinary.key)
            assertEquals(false, parsedBinary.state)

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `SubscribeStates pushes initial states for sensor text sensor number and select`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)
            val sensorKey = keyFromObjectId("ha_entity_count")
            val textSensorKey = keyFromObjectId("ha_connection_state")
            val numberKey = keyFromObjectId("idle_timeout_sec")
            val selectKey = keyFromObjectId("day_screensaver_mode")
            val entities =
                listOf(
                    EsphomeEntity.Sensor(
                        key = sensorKey,
                        objectId = "ha_entity_count",
                        name = "HA Entity Count",
                        state = MutableStateFlow(2f),
                    ),
                    EsphomeEntity.TextSensor(
                        key = textSensorKey,
                        objectId = "ha_connection_state",
                        name = "HA Connection State",
                        state = MutableStateFlow("Connected"),
                    ),
                    EsphomeEntity.Number(
                        key = numberKey,
                        objectId = "idle_timeout_sec",
                        name = "Idle Timeout",
                        state = MutableStateFlow(300f),
                        minValue = 0f,
                        maxValue = 1800f,
                        step = 30f,
                        unitOfMeasurement = "s",
                        onCommand = {},
                    ),
                    EsphomeEntity.Select(
                        key = selectKey,
                        objectId = "day_screensaver_mode",
                        name = "Day Screensaver Mode",
                        state = MutableStateFlow("photos"),
                        options =
                            listOf(
                                "off",
                                "photos",
                                "immich",
                                "media_library",
                                "clock",
                                "black",
                            ),
                        onCommand = {},
                    ),
                )
            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest.newBuilder().build().toByteArray(),
            )
            serverToClient.readEsphomeFrame()
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.SUBSCRIBE_STATES_REQUEST,
                org.esphome.api.SubscribeStatesRequest
                    .newBuilder()
                    .build()
                    .toByteArray(),
            )

            val sensor = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.SENSOR_STATE_RESPONSE, sensor.messageType)
            val sensorResponse = SensorStateResponse.parseFrom(sensor.payload)
            assertEquals(sensorKey, sensorResponse.key)
            assertEquals(
                2f,
                sensorResponse.state,
                0.01f,
            )

            val textSensor = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.TEXT_SENSOR_STATE_RESPONSE, textSensor.messageType)
            val textSensorResponse = TextSensorStateResponse.parseFrom(textSensor.payload)
            assertEquals(textSensorKey, textSensorResponse.key)
            assertEquals(
                "Connected",
                textSensorResponse.state,
            )

            val number = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.NUMBER_STATE_RESPONSE, number.messageType)
            val numberResponse = NumberStateResponse.parseFrom(number.payload)
            assertEquals(numberKey, numberResponse.key)
            assertEquals(
                300f,
                numberResponse.state,
                0.01f,
            )

            val select = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.SELECT_STATE_RESPONSE, select.messageType)
            val selectResponse = SelectStateResponse.parseFrom(select.payload)
            assertEquals(selectKey, selectResponse.key)
            assertEquals(
                "photos",
                selectResponse.state,
            )

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `SwitchCommandRequest invokes the onCommand callback`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)

            val received = mutableListOf<Boolean>()
            val keepScreenOnState = MutableStateFlow(false)
            val entities =
                listOf(
                    EsphomeEntity.Switch(
                        key = keyFromObjectId("keep_screen_on"),
                        objectId = "keep_screen_on",
                        name = "Keep Screen On",
                        state = keepScreenOnState,
                        onCommand = {
                            received.add(it)
                            keepScreenOnState.value = it
                        },
                    ),
                )

            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(EsphomeMessageType.HELLO_REQUEST, HelloRequest.newBuilder().build().toByteArray())
            serverToClient.readEsphomeFrame()

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.SWITCH_COMMAND_REQUEST,
                org.esphome.api.SwitchCommandRequest
                    .newBuilder()
                    .setKey(keyFromObjectId("keep_screen_on"))
                    .setState(true)
                    .build()
                    .toByteArray(),
            )

            kotlinx.coroutines.delay(50)
            assertEquals(listOf(true), received)

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `NumberCommandRequest invokes the onCommand callback`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)
            val received = mutableListOf<Float>()
            val entities =
                listOf(
                    EsphomeEntity.Number(
                        key = keyFromObjectId("idle_timeout_sec"),
                        objectId = "idle_timeout_sec",
                        name = "Idle Timeout",
                        state = MutableStateFlow(300f),
                        minValue = 0f,
                        maxValue = 1800f,
                        step = 30f,
                        unitOfMeasurement = "s",
                        onCommand = { value -> received.add(value) },
                    ),
                )
            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest.newBuilder().build().toByteArray(),
            )
            serverToClient.readEsphomeFrame()
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.NUMBER_COMMAND_REQUEST,
                org.esphome.api.NumberCommandRequest
                    .newBuilder()
                    .setKey(keyFromObjectId("idle_timeout_sec"))
                    .setState(600f)
                    .build()
                    .toByteArray(),
            )

            kotlinx.coroutines.delay(50)
            assertEquals(listOf(600f), received)

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `NumberCommandRequest ignores out of range state`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)
            val received = mutableListOf<Float>()
            val entities =
                listOf(
                    EsphomeEntity.Number(
                        key = keyFromObjectId("idle_timeout_sec"),
                        objectId = "idle_timeout_sec",
                        name = "Idle Timeout",
                        state = MutableStateFlow(300f),
                        minValue = 0f,
                        maxValue = 1800f,
                        step = 30f,
                        unitOfMeasurement = "s",
                        onCommand = { value -> received.add(value) },
                    ),
                )
            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest.newBuilder().build().toByteArray(),
            )
            serverToClient.readEsphomeFrame()
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.NUMBER_COMMAND_REQUEST,
                org.esphome.api.NumberCommandRequest
                    .newBuilder()
                    .setKey(keyFromObjectId("idle_timeout_sec"))
                    .setState(1801f)
                    .build()
                    .toByteArray(),
            )

            kotlinx.coroutines.delay(50)
            assertEquals(emptyList<Float>(), received)

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `NumberCommandRequest ignores non finite state`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)
            val received = mutableListOf<Float>()
            val entities =
                listOf(
                    EsphomeEntity.Number(
                        key = keyFromObjectId("idle_timeout_sec"),
                        objectId = "idle_timeout_sec",
                        name = "Idle Timeout",
                        state = MutableStateFlow(300f),
                        minValue = 0f,
                        maxValue = 1800f,
                        step = 30f,
                        unitOfMeasurement = "s",
                        onCommand = { value -> received.add(value) },
                    ),
                )
            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest.newBuilder().build().toByteArray(),
            )
            serverToClient.readEsphomeFrame()
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.NUMBER_COMMAND_REQUEST,
                org.esphome.api.NumberCommandRequest
                    .newBuilder()
                    .setKey(keyFromObjectId("idle_timeout_sec"))
                    .setState(Float.NaN)
                    .build()
                    .toByteArray(),
            )

            kotlinx.coroutines.delay(50)
            assertEquals(emptyList<Float>(), received)

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `SelectCommandRequest invokes the onCommand callback`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)
            val received = mutableListOf<String>()
            val entities =
                listOf(
                    EsphomeEntity.Select(
                        key = keyFromObjectId("day_screensaver_mode"),
                        objectId = "day_screensaver_mode",
                        name = "Day Screensaver Mode",
                        state = MutableStateFlow("photos"),
                        options = listOf("off", "photos", "immich", "media_library", "clock", "black"),
                        onCommand = { value -> received.add(value) },
                    ),
                )
            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest.newBuilder().build().toByteArray(),
            )
            serverToClient.readEsphomeFrame()
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.SELECT_COMMAND_REQUEST,
                org.esphome.api.SelectCommandRequest
                    .newBuilder()
                    .setKey(keyFromObjectId("day_screensaver_mode"))
                    .setState("clock")
                    .build()
                    .toByteArray(),
            )

            kotlinx.coroutines.delay(50)
            assertEquals(listOf("clock"), received)

            clientToServer.close()
            job.cancel()
        }

    @Test
    fun `SelectCommandRequest ignores state outside options`() =
        runTest(UnconfinedTestDispatcher()) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)
            val received = mutableListOf<String>()
            val entities =
                listOf(
                    EsphomeEntity.Select(
                        key = keyFromObjectId("day_screensaver_mode"),
                        objectId = "day_screensaver_mode",
                        name = "Day Screensaver Mode",
                        state = MutableStateFlow("photos"),
                        options = listOf("off", "photos", "immich", "media_library", "clock", "black"),
                        onCommand = { value -> received.add(value) },
                    ),
                )
            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = entities,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest.newBuilder().build().toByteArray(),
            )
            serverToClient.readEsphomeFrame()
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.SELECT_COMMAND_REQUEST,
                org.esphome.api.SelectCommandRequest
                    .newBuilder()
                    .setKey(keyFromObjectId("day_screensaver_mode"))
                    .setState("matrix")
                    .build()
                    .toByteArray(),
            )

            kotlinx.coroutines.delay(50)
            assertEquals(emptyList<String>(), received)

            clientToServer.close()
            job.cancel()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `idle client past timeout is disconnected and coroutine exits`() =
        runTest(UnconfinedTestDispatcher(), timeout = kotlin.time.Duration.parse("10s")) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)

            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = emptyList(),
                    idleTimeoutMs = 5_000L,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest
                    .newBuilder()
                    .setClientInfo("idle-client")
                    .build()
                    .toByteArray(),
            )
            val hello = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.HELLO_RESPONSE, hello.messageType)

            // Client goes silent. Advance virtual time past the idle threshold.
            kotlinx.coroutines.delay(6_000L)

            assertTrue("connection coroutine should have completed after idle timeout", job.isCompleted)
            assertFalse("connection coroutine should not be active", job.isActive)

            clientToServer.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `client that pings before timeout stays connected`() =
        runTest(UnconfinedTestDispatcher(), timeout = kotlin.time.Duration.parse("10s")) {
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)

            val connection =
                EsphomeConnection(
                    transport = PlainTransport(input = clientToServer, output = serverToClient),
                    deviceInfo = deviceInfo,
                    entities = emptyList(),
                    idleTimeoutMs = 5_000L,
                )
            val job = launch { connection.run() }

            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest
                    .newBuilder()
                    .setClientInfo("ping-client")
                    .build()
                    .toByteArray(),
            )
            serverToClient.readEsphomeFrame() // HelloResponse

            // Ping just before the deadline; the timeout should reset.
            kotlinx.coroutines.delay(3_000L)
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.PING_REQUEST,
                PingRequest.newBuilder().build().toByteArray(),
            )
            val pingResponse = serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.PING_RESPONSE, pingResponse.messageType)
            PingResponse.parseFrom(pingResponse.payload)

            // Advance more, but not enough since last ping to trigger timeout.
            kotlinx.coroutines.delay(3_000L)

            assertTrue("connection should still be active after a keepalive ping", job.isActive)

            clientToServer.close()
            job.cancel()
        }
}

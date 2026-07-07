package com.superdash.esphome

import com.superdash.core.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.esphome.api.BinarySensorStateResponse
import org.esphome.api.ButtonCommandRequest
import org.esphome.api.CameraImageRequest
import org.esphome.api.DeviceInfoResponse
import org.esphome.api.DisconnectResponse
import org.esphome.api.HelloRequest
import org.esphome.api.HelloResponse
import org.esphome.api.ListEntitiesBinarySensorResponse
import org.esphome.api.ListEntitiesButtonResponse
import org.esphome.api.ListEntitiesCameraResponse
import org.esphome.api.ListEntitiesDoneResponse
import org.esphome.api.ListEntitiesNumberResponse
import org.esphome.api.ListEntitiesSelectResponse
import org.esphome.api.ListEntitiesSensorResponse
import org.esphome.api.ListEntitiesSwitchResponse
import org.esphome.api.ListEntitiesTextSensorResponse
import org.esphome.api.NumberCommandRequest
import org.esphome.api.NumberStateResponse
import org.esphome.api.PingResponse
import org.esphome.api.SelectCommandRequest
import org.esphome.api.SelectStateResponse
import org.esphome.api.SensorStateResponse
import org.esphome.api.SwitchCommandRequest
import org.esphome.api.SwitchStateResponse
import org.esphome.api.TextSensorStateResponse

private val log = Log("EsphomeConnection")

/** Idle read deadline. Home Assistant's native-API client pings every 60s; we
 *  pick 90s so a healthy client comfortably stays connected while a wedged
 *  client (TCP open, no bytes) is reaped before it pins fds across HA's
 *  reconnect storms. */
internal const val DEFAULT_IDLE_TIMEOUT_MS = 90_000L

/** How long a CameraImageRequest(stream) keeps the frame push alive. HA
 *  refreshes the window with repeated stream requests while a client watches. */
internal const val CAMERA_STREAM_WINDOW_NANOS = 5_000_000_000L

internal data class EsphomeDeviceInfo(
    val name: String,
    val macAddress: String,
    val esphomeVersion: String,
    val compilationTime: String,
    val model: String,
    val manufacturer: String,
    val friendlyName: String,
)

internal class EsphomeConnection(
    private val transport: EsphomeTransport,
    private val deviceInfo: EsphomeDeviceInfo,
    private val entities: List<EsphomeEntity>,
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private var helloDone = false
    private var stateJobs: List<Job> = emptyList()
    private var cameraStreamJob: Job? = null
    private val cameraSendMutex = Mutex()

    @Volatile private var cameraStreamDeadlineNanos = Long.MIN_VALUE

    suspend fun run() =
        coroutineScope {
            try {
                runUntilDisconnect(this)
            } catch (timeout: TimeoutCancellationException) {
                log.i("idle client timed out", "afterMs" to idleTimeoutMs)
            } catch (throwable: Throwable) {
                log.w("connection ended", throwable)
            } finally {
                stateJobs.forEach { it.cancel() }
                cameraStreamJob?.cancel()
            }
        }

    private suspend fun runUntilDisconnect(scope: CoroutineScope) {
        while (true) {
            val frame = withTimeout(idleTimeoutMs) { transport.readFrame() }
            when (frame.messageType) {
                EsphomeMessageType.HELLO_REQUEST -> handleHello(frame.payload)
                EsphomeMessageType.DISCONNECT_REQUEST -> {
                    transport.writeFrame(
                        EsphomeMessageType.DISCONNECT_RESPONSE,
                        DisconnectResponse.newBuilder().build().toByteArray(),
                    )
                    return
                }
                EsphomeMessageType.PING_REQUEST -> {
                    transport.writeFrame(
                        EsphomeMessageType.PING_RESPONSE,
                        PingResponse.newBuilder().build().toByteArray(),
                    )
                }
                EsphomeMessageType.DEVICE_INFO_REQUEST -> {
                    if (!helloDone) {
                        log.w("device_info before hello; dropping")
                        continue
                    }
                    handleDeviceInfo()
                }
                EsphomeMessageType.LIST_ENTITIES_REQUEST -> {
                    if (!helloDone) {
                        log.w("list_entities before hello; dropping")
                        continue
                    }
                    handleListEntities()
                }
                EsphomeMessageType.SUBSCRIBE_STATES_REQUEST -> {
                    if (!helloDone) {
                        log.w("subscribe_states before hello; dropping")
                        continue
                    }
                    stateJobs.forEach { it.cancel() }
                    stateJobs = subscribeStates(scope)
                }
                EsphomeMessageType.SWITCH_COMMAND_REQUEST -> handleSwitchCommand(frame.payload)
                EsphomeMessageType.NUMBER_COMMAND_REQUEST -> handleNumberCommand(frame.payload)
                EsphomeMessageType.SELECT_COMMAND_REQUEST -> handleSelectCommand(frame.payload)
                EsphomeMessageType.BUTTON_COMMAND_REQUEST -> handleButtonCommand(frame.payload)
                EsphomeMessageType.CAMERA_IMAGE_REQUEST -> handleCameraImageRequest(frame.payload, scope)
                else -> log.w("ignoring unhandled message type", null, "type" to frame.messageType)
            }
        }
    }

    private suspend fun handleHello(payload: ByteArray) {
        val request = HelloRequest.parseFrom(payload)
        log.i("hello", "client" to request.clientInfo)
        val response =
            HelloResponse
                .newBuilder()
                .setApiVersionMajor(1)
                .setApiVersionMinor(14)
                .setServerInfo("superdash ${deviceInfo.esphomeVersion}")
                .setName(deviceInfo.name)
                .build()
        transport.writeFrame(EsphomeMessageType.HELLO_RESPONSE, response.toByteArray())
        helloDone = true
    }

    private suspend fun handleDeviceInfo() {
        val response =
            DeviceInfoResponse
                .newBuilder()
                .setUsesPassword(false)
                .setName(deviceInfo.name)
                .setMacAddress(deviceInfo.macAddress)
                .setEsphomeVersion(deviceInfo.esphomeVersion)
                .setCompilationTime(deviceInfo.compilationTime)
                .setModel(deviceInfo.model)
                .setManufacturer(deviceInfo.manufacturer)
                .setFriendlyName(deviceInfo.friendlyName)
                .build()
        transport.writeFrame(EsphomeMessageType.DEVICE_INFO_RESPONSE, response.toByteArray())
    }

    private suspend fun handleListEntities() {
        for (entity in entities) {
            when (entity) {
                is EsphomeEntity.Switch -> {
                    val msg =
                        ListEntitiesSwitchResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_SWITCH_RESPONSE, msg.toByteArray())
                }
                is EsphomeEntity.BinarySensor -> {
                    val msg =
                        ListEntitiesBinarySensorResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .setDeviceClass(entity.deviceClass)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_BINARY_SENSOR_RESPONSE, msg.toByteArray())
                }
                is EsphomeEntity.Sensor -> {
                    val msg =
                        ListEntitiesSensorResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .setUnitOfMeasurement(entity.unitOfMeasurement)
                            .setAccuracyDecimals(entity.accuracyDecimals)
                            .setDeviceClass(entity.deviceClass)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_SENSOR_RESPONSE, msg.toByteArray())
                }
                is EsphomeEntity.TextSensor -> {
                    val msg =
                        ListEntitiesTextSensorResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .setDeviceClass(entity.deviceClass)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_TEXT_SENSOR_RESPONSE, msg.toByteArray())
                }
                is EsphomeEntity.Number -> {
                    val msg =
                        ListEntitiesNumberResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .setMinValue(entity.minValue)
                            .setMaxValue(entity.maxValue)
                            .setStep(entity.step)
                            .setUnitOfMeasurement(entity.unitOfMeasurement)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_NUMBER_RESPONSE, msg.toByteArray())
                }
                is EsphomeEntity.Select -> {
                    val msg =
                        ListEntitiesSelectResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .addAllOptions(entity.options)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_SELECT_RESPONSE, msg.toByteArray())
                }
                is EsphomeEntity.Button -> {
                    val msg =
                        ListEntitiesButtonResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_BUTTON_RESPONSE, msg.toByteArray())
                }
                is EsphomeEntity.Camera -> {
                    val msg =
                        ListEntitiesCameraResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_CAMERA_RESPONSE, msg.toByteArray())
                }
            }
        }
        transport.writeFrame(
            EsphomeMessageType.LIST_ENTITIES_DONE_RESPONSE,
            ListEntitiesDoneResponse.newBuilder().build().toByteArray(),
        )
    }

    /** Returns one Job per stateful entity. Caller cancels them on
     *  re-subscribe / connection close. Each collector is a child of the
     *  caller's scope (the connection's `coroutineScope { }`), so closing the
     *  connection's run() call propagates cancellation cleanly. Buttons are
     *  stateless and contribute no collector. */
    private fun subscribeStates(scope: CoroutineScope): List<Job> =
        entities.mapNotNull { entity ->
            when (entity) {
                is EsphomeEntity.Switch ->
                    scope.launch {
                        entity.state.distinctUntilChanged().collect { value ->
                            val msg =
                                SwitchStateResponse
                                    .newBuilder()
                                    .setKey(entity.key)
                                    .setState(value)
                                    .build()
                            transport.writeFrame(EsphomeMessageType.SWITCH_STATE_RESPONSE, msg.toByteArray())
                        }
                    }
                is EsphomeEntity.BinarySensor ->
                    scope.launch {
                        entity.state.distinctUntilChanged().collect { value ->
                            val msg =
                                BinarySensorStateResponse
                                    .newBuilder()
                                    .setKey(entity.key)
                                    .setState(value)
                                    .build()
                            transport.writeFrame(
                                EsphomeMessageType.BINARY_SENSOR_STATE_RESPONSE,
                                msg.toByteArray(),
                            )
                        }
                    }
                is EsphomeEntity.Button -> null
                is EsphomeEntity.Camera -> null
                is EsphomeEntity.Sensor ->
                    scope.launch {
                        entity.state.distinctUntilChanged().collect { value ->
                            val msg =
                                SensorStateResponse
                                    .newBuilder()
                                    .setKey(entity.key)
                                    .setState(value)
                                    .build()
                            transport.writeFrame(EsphomeMessageType.SENSOR_STATE_RESPONSE, msg.toByteArray())
                        }
                    }
                is EsphomeEntity.TextSensor ->
                    scope.launch {
                        entity.state.distinctUntilChanged().collect { value ->
                            val msg =
                                TextSensorStateResponse
                                    .newBuilder()
                                    .setKey(entity.key)
                                    .setState(value)
                                    .build()
                            transport.writeFrame(
                                EsphomeMessageType.TEXT_SENSOR_STATE_RESPONSE,
                                msg.toByteArray(),
                            )
                        }
                    }
                is EsphomeEntity.Number ->
                    scope.launch {
                        entity.state.distinctUntilChanged().collect { value ->
                            val msg =
                                NumberStateResponse
                                    .newBuilder()
                                    .setKey(entity.key)
                                    .setState(value)
                                    .build()
                            transport.writeFrame(EsphomeMessageType.NUMBER_STATE_RESPONSE, msg.toByteArray())
                        }
                    }
                is EsphomeEntity.Select ->
                    scope.launch {
                        entity.state.distinctUntilChanged().collect { value ->
                            val msg =
                                SelectStateResponse
                                    .newBuilder()
                                    .setKey(entity.key)
                                    .setState(value)
                                    .build()
                            transport.writeFrame(EsphomeMessageType.SELECT_STATE_RESPONSE, msg.toByteArray())
                        }
                    }
            }
        }

    private suspend fun handleSwitchCommand(payload: ByteArray) {
        val request = SwitchCommandRequest.parseFrom(payload)
        val target = entities.filterIsInstance<EsphomeEntity.Switch>().firstOrNull { it.key == request.key }
        if (target == null) {
            log.w("unknown switch key", null, "key" to request.key)
            return
        }
        runCatching { target.onCommand(request.state) }
            .onFailure { log.w("switch command failed", it, "key" to request.key) }
    }

    private suspend fun handleNumberCommand(payload: ByteArray) {
        val request = NumberCommandRequest.parseFrom(payload)
        val target = entities.filterIsInstance<EsphomeEntity.Number>().firstOrNull { it.key == request.key }
        if (target == null) {
            log.w("unknown number key", null, "key" to request.key)
            return
        }
        if (!request.state.isFinite() || request.state < target.minValue || request.state > target.maxValue) {
            log.w(
                "number command out of range",
                null,
                "key" to request.key,
                "state" to request.state,
            )
            return
        }
        runCatching { target.onCommand(request.state) }
            .onFailure { log.w("number command failed", it, "key" to request.key) }
    }

    private suspend fun handleSelectCommand(payload: ByteArray) {
        val request = SelectCommandRequest.parseFrom(payload)
        val target = entities.filterIsInstance<EsphomeEntity.Select>().firstOrNull { it.key == request.key }
        if (target == null) {
            log.w("unknown select key", null, "key" to request.key)
            return
        }
        if (request.state !in target.options) {
            log.w("invalid select option", null, "key" to request.key, "state" to request.state)
            return
        }
        runCatching { target.onCommand(request.state) }
            .onFailure { log.w("select command failed", it, "key" to request.key) }
    }

    private suspend fun handleButtonCommand(payload: ByteArray) {
        val request = ButtonCommandRequest.parseFrom(payload)
        val target = entities.filterIsInstance<EsphomeEntity.Button>().firstOrNull { it.key == request.key }
        if (target == null) {
            log.w("unknown button key", null, "key" to request.key)
            return
        }
        runCatching { target.onPress() }
            .onFailure { log.w("button press failed", it, "key" to request.key) }
    }

    private suspend fun handleCameraImageRequest(
        payload: ByteArray,
        scope: CoroutineScope,
    ) {
        val request = CameraImageRequest.parseFrom(payload)
        val camera = entities.filterIsInstance<EsphomeEntity.Camera>().firstOrNull()
        if (camera == null) {
            log.w("camera image request but no camera entity")
            return
        }
        if (request.single) {
            val jpeg =
                runCatching { camera.latestJpeg() }
                    .onFailure { log.w("latestJpeg failed", it) }
                    .getOrNull()
            if (jpeg == null) {
                log.i("no camera frame available for single request")
            } else {
                sendCameraImage(camera.key, jpeg)
            }
        }
        if (request.stream) {
            cameraStreamDeadlineNanos = nanoTime() + CAMERA_STREAM_WINDOW_NANOS
            if (cameraStreamJob?.isActive != true) {
                cameraStreamJob =
                    scope.launch {
                        camera.frames
                            .takeWhile { nanoTime() < cameraStreamDeadlineNanos }
                            .collect { jpeg -> sendCameraImage(camera.key, jpeg) }
                    }
            }
        }
    }

    private suspend fun sendCameraImage(
        key: Int,
        jpeg: ByteArray,
    ) {
        cameraSendMutex.withLock {
            for (chunk in cameraImageChunks(key, jpeg)) {
                transport.writeFrame(EsphomeMessageType.CAMERA_IMAGE_RESPONSE, chunk.toByteArray())
            }
        }
    }
}

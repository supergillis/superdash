package com.superdash.esphome

import android.content.Context
import com.superdash.core.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val screensaverModeOptions = listOf("off", "photos", "immich", "media_library", "clock", "black")
private val overlayPositionOptions = listOf("bottom_left", "bottom_right", "top_left", "top_right", "random")
private val primarySttOptions = listOf("ha_assist", "whisper", "moonshine")
private val secondarySttOptions = listOf("none", "ha_assist", "whisper", "moonshine")
private val voiceAssistOptions = listOf("ha_assist", "whisper", "moonshine")
private val mediaLibraryOrderOptions = listOf("shuffle", "chronological")
private val motionModeOptions = listOf("off", "motion", "person")
private val log = Log("EsphomeBindings")

internal fun switchEntity(
    objectId: String,
    name: String,
    state: Flow<Boolean>,
    onCommand: suspend (Boolean) -> Unit,
): EsphomeEntity.Switch =
    EsphomeEntity.Switch(
        key = keyFromObjectId(objectId),
        objectId = objectId,
        name = name,
        state = state,
        onCommand = onCommand,
    )

internal fun binarySensorEntity(
    objectId: String,
    name: String,
    state: Flow<Boolean>,
    deviceClass: String = "",
): EsphomeEntity.BinarySensor =
    EsphomeEntity.BinarySensor(
        key = keyFromObjectId(objectId),
        objectId = objectId,
        name = name,
        state = state,
        deviceClass = deviceClass,
    )

internal fun sensorEntity(
    objectId: String,
    name: String,
    state: Flow<Float>,
    unitOfMeasurement: String = "",
    accuracyDecimals: Int = 0,
    deviceClass: String = "",
): EsphomeEntity.Sensor =
    EsphomeEntity.Sensor(
        key = keyFromObjectId(objectId),
        objectId = objectId,
        name = name,
        state = state,
        unitOfMeasurement = unitOfMeasurement,
        accuracyDecimals = accuracyDecimals,
        deviceClass = deviceClass,
    )

internal fun textSensorEntity(
    objectId: String,
    name: String,
    state: Flow<String>,
    deviceClass: String = "",
): EsphomeEntity.TextSensor =
    EsphomeEntity.TextSensor(
        key = keyFromObjectId(objectId),
        objectId = objectId,
        name = name,
        state = state,
        deviceClass = deviceClass,
    )

internal fun numberEntity(
    objectId: String,
    name: String,
    state: Flow<Float>,
    minValue: Float,
    maxValue: Float,
    step: Float,
    unitOfMeasurement: String = "",
    onCommand: suspend (Float) -> Unit,
): EsphomeEntity.Number =
    EsphomeEntity.Number(
        key = keyFromObjectId(objectId),
        objectId = objectId,
        name = name,
        state = state,
        minValue = minValue,
        maxValue = maxValue,
        step = step,
        unitOfMeasurement = unitOfMeasurement,
        onCommand = { value ->
            val rounded = (value / step).roundToInt() * step
            onCommand(rounded.coerceIn(minValue, maxValue))
        },
    )

internal fun selectEntity(
    objectId: String,
    name: String,
    state: Flow<String>,
    options: List<String>,
    onCommand: suspend (String) -> Unit,
): EsphomeEntity.Select =
    EsphomeEntity.Select(
        key = keyFromObjectId(objectId),
        objectId = objectId,
        name = name,
        state = state,
        options = options,
        onCommand = { value ->
            if (value in options) {
                onCommand(value)
            } else {
                log.w("invalid select option", null, "objectId" to objectId, "value" to value)
            }
        },
    )

internal fun esphomeEntities(
    device: EsphomeDeviceMetadata,
    kiosk: EsphomeKioskBindings,
    voice: EsphomeVoiceBindings,
    screensaver: EsphomeScreensaverBindings,
    doorbell: EsphomeDoorbellBindings,
    nightMode: EsphomeNightModeBindings,
    ha: EsphomeHaBindings,
    camera: EsphomeCameraBindings,
): List<EsphomeEntity> =
    listOf(
        switchEntity(
            objectId = "keep_screen_on",
            name = "Keep Screen On",
            state = kiosk.keepScreenOn,
            onCommand = kiosk.setKeepScreenOn,
        ),
        switchEntity(
            objectId = "start_on_boot",
            name = "Start On Boot",
            state = kiosk.startOnBoot,
            onCommand = kiosk.setStartOnBoot,
        ),
        switchEntity(
            objectId = "night_mode",
            name = "Night Mode",
            state = nightMode.nightMode,
            onCommand = nightMode.setNightMode,
        ),
        switchEntity(
            objectId = "voice_enabled",
            name = "Voice Enabled",
            state = voice.voiceEnabled,
            onCommand = voice.setVoiceEnabled,
        ),
        switchEntity(
            objectId = "doorbell_enabled",
            name = "Doorbell Enabled",
            state = doorbell.doorbellEnabled,
            onCommand = doorbell.setDoorbellEnabled,
        ),
        switchEntity(
            objectId = "launch_on_wake",
            name = "Launch On Wake",
            state = kiosk.launchOnWake,
            onCommand = kiosk.setLaunchOnWake,
        ),
        binarySensorEntity(
            objectId = "screen_on",
            name = "Screen On",
            state = kiosk.screenOn,
        ),
        binarySensorEntity(
            objectId = "in_screensaver",
            name = "In Screensaver",
            state = screensaver.inScreensaver,
        ),
        binarySensorEntity(
            objectId = "doorbell_ringing",
            name = "Doorbell Ringing",
            state = doorbell.doorbellRinging,
        ),
        binarySensorEntity(
            objectId = "voice_active",
            name = "Voice Active",
            state = voice.voiceActive,
        ),
        sensorEntity(
            objectId = "ha_entity_count",
            name = "HA Entity Count",
            state = ha.haEntityCount,
        ),
        sensorEntity(
            objectId = "doorbell_count",
            name = "Doorbell Count",
            state = doorbell.doorbellCount,
        ),
        textSensorEntity(
            objectId = "ha_connection_state",
            name = "HA Connection State",
            state = ha.haConnectionState,
        ),
        textSensorEntity(
            objectId = "voice_state",
            name = "Voice State",
            state = voice.voiceState,
        ),
        textSensorEntity(
            objectId = "active_wake_word_state",
            name = "Active Wake Word State",
            state = voice.activeWakeWordState,
        ),
        textSensorEntity(
            objectId = "day_screensaver_mode_state",
            name = "Day Screensaver Mode State",
            state = screensaver.dayScreensaverModeState,
        ),
        textSensorEntity(
            objectId = "night_screensaver_mode_state",
            name = "Night Screensaver Mode State",
            state = screensaver.nightScreensaverModeState,
        ),
        textSensorEntity(
            objectId = "weather_entity_id",
            name = "Weather Entity ID",
            state = screensaver.weatherEntityId,
        ),
        textSensorEntity(
            objectId = "media_library_source_title",
            name = "Media Library Source Title",
            state = screensaver.mediaLibrarySourceTitle,
        ),
        textSensorEntity(
            objectId = "app_version",
            name = "App Version",
            state = device.appVersion,
        ),
        numberEntity(
            objectId = "vad_silence_ms",
            name = "VAD Silence",
            state = voice.vadSilenceMs,
            minValue = 250f,
            maxValue = 2500f,
            step = 250f,
            unitOfMeasurement = "ms",
            onCommand = voice.setVadSilenceMs,
        ),
        numberEntity(
            objectId = "idle_timeout_sec",
            name = "Idle Timeout",
            state = screensaver.idleTimeoutSec,
            minValue = 0f,
            maxValue = 1800f,
            step = 30f,
            unitOfMeasurement = "s",
            onCommand = screensaver.setIdleTimeoutSec,
        ),
        numberEntity(
            objectId = "picture_spacing_dp",
            name = "Picture Spacing",
            state = screensaver.pictureSpacingDp,
            minValue = 0f,
            maxValue = 48f,
            step = 4f,
            unitOfMeasurement = "dp",
            onCommand = screensaver.setPictureSpacingDp,
        ),
        numberEntity(
            objectId = "doorbell_auto_close_sec",
            name = "Doorbell Auto Close",
            state = doorbell.doorbellAutoCloseSec,
            minValue = 0f,
            maxValue = 300f,
            step = 10f,
            unitOfMeasurement = "s",
            onCommand = doorbell.setDoorbellAutoCloseSec,
        ),
        selectEntity(
            objectId = "day_screensaver_mode",
            name = "Day Screensaver Mode",
            state = screensaver.dayScreensaverMode,
            options = screensaverModeOptions,
            onCommand = screensaver.setDayScreensaverMode,
        ),
        selectEntity(
            objectId = "night_screensaver_mode",
            name = "Night Screensaver Mode",
            state = screensaver.nightScreensaverMode,
            options = screensaverModeOptions,
            onCommand = screensaver.setNightScreensaverMode,
        ),
        selectEntity(
            objectId = "overlay_position",
            name = "Overlay Position",
            state = screensaver.overlayPosition,
            options = overlayPositionOptions,
            onCommand = screensaver.setOverlayPosition,
        ),
        selectEntity(
            objectId = "primary_stt_provider",
            name = "Primary STT Provider",
            state = voice.primarySttProvider,
            options = primarySttOptions,
            onCommand = voice.setPrimarySttProvider,
        ),
        selectEntity(
            objectId = "secondary_stt_provider",
            name = "Secondary STT Provider",
            state = voice.secondarySttProvider,
            options = secondarySttOptions,
            onCommand = voice.setSecondarySttProvider,
        ),
        selectEntity(
            objectId = "voice_assist_provider",
            name = "Voice Assist Provider",
            state = voice.voiceAssistProvider,
            options = voiceAssistOptions,
            onCommand = voice.setVoiceAssistProvider,
        ),
        selectEntity(
            objectId = "active_wake_word",
            name = "Active Wake Word",
            state = voice.activeWakeWord,
            options = listOf("hey_jarvis"),
            onCommand = voice.setActiveWakeWord,
        ),
        selectEntity(
            objectId = "media_library_order",
            name = "Media Library Order",
            state = screensaver.mediaLibraryOrder,
            options = mediaLibraryOrderOptions,
            onCommand = screensaver.setMediaLibraryOrder,
        ),
        EsphomeEntity.Button(
            key = keyFromObjectId("refresh_webview"),
            objectId = "refresh_webview",
            name = "Refresh WebView",
            onPress = kiosk.refreshWebView,
        ),
        EsphomeEntity.Button(
            key = keyFromObjectId("restart_app"),
            objectId = "restart_app",
            name = "Restart App",
            onPress = kiosk.restartApp,
        ),
        EsphomeEntity.Button(
            key = keyFromObjectId("start_screensaver"),
            objectId = "start_screensaver",
            name = "Start Screensaver",
            onPress = screensaver.startScreensaver,
        ),
        EsphomeEntity.Button(
            key = keyFromObjectId("stop_screensaver"),
            objectId = "stop_screensaver",
            name = "Stop Screensaver",
            onPress = screensaver.stopScreensaver,
        ),
        switchEntity(
            objectId = "camera_enabled",
            name = "Camera Enabled",
            state = camera.cameraEnabled,
            onCommand = camera.setCameraEnabled,
        ),
        switchEntity(
            objectId = "wake_on_motion",
            name = "Wake On Motion",
            state = camera.wakeOnMotion,
            onCommand = camera.setWakeOnMotion,
        ),
        binarySensorEntity(
            objectId = "motion",
            name = "Motion",
            state = camera.motionDetected,
            deviceClass = "motion",
        ),
        selectEntity(
            objectId = "motion_detection_mode",
            name = "Motion Detection Mode",
            state = camera.motionMode,
            options = motionModeOptions,
            onCommand = camera.setMotionMode,
        ),
        numberEntity(
            objectId = "motion_sensitivity",
            name = "Motion Sensitivity",
            state = camera.motionSensitivity,
            minValue = 0f,
            maxValue = 100f,
            step = 5f,
            unitOfMeasurement = "%",
            onCommand = camera.setMotionSensitivity,
        ),
        numberEntity(
            objectId = "motion_clear_delay_sec",
            name = "Motion Clear Delay",
            state = camera.motionClearDelaySec,
            minValue = 0f,
            maxValue = 120f,
            step = 5f,
            unitOfMeasurement = "s",
            onCommand = camera.setMotionClearDelaySec,
        ),
        textSensorEntity(
            objectId = "camera_status",
            name = "Camera Status",
            state = camera.cameraStatus,
        ),
        EsphomeEntity.Camera(
            key = keyFromObjectId("camera"),
            objectId = "camera",
            name = "Camera",
            frames = camera.jpegFrames,
            latestJpeg = camera.latestJpeg,
        ),
    )

/** Owns the ESPHome stack and binds it to host-provided sources and sinks. */
class EsphomeBindings(
    appContext: Context,
    scope: CoroutineScope,
    enabled: Flow<Boolean>,
    noisePsk: Flow<ByteArray?> = kotlinx.coroutines.flow.flowOf(null),
    device: EsphomeDeviceMetadata,
    kiosk: EsphomeKioskBindings,
    voice: EsphomeVoiceBindings,
    screensaver: EsphomeScreensaverBindings,
    doorbell: EsphomeDoorbellBindings,
    nightMode: EsphomeNightModeBindings,
    ha: EsphomeHaBindings,
    camera: EsphomeCameraBindings,
) {
    private val deviceInfo: EsphomeDeviceInfo =
        EsphomeDeviceInfo(
            name = "superdash-${device.stableDeviceId.take(8)}",
            macAddress = deriveMacFromStableId(device.stableDeviceId),
            esphomeVersion = "2026.4.5-superdash-${device.appVersionName}",
            compilationTime = "",
            model = device.deviceModel,
            manufacturer = device.deviceManufacturer,
            friendlyName = device.friendlyName,
        )

    @Volatile private var currentConfig: EsphomeNoiseConfig = EsphomeNoiseConfig.PlainOnly

    @Volatile private var currentMdns: EsphomeMdns =
        EsphomeMdns(appContext, deviceInfo, noiseEnabled = false)

    private val server: EsphomeServer =
        EsphomeServer(
            scope = scope,
            enabled = enabled,
            deviceInfo = deviceInfo,
            entities = {
                esphomeEntities(
                    device = device,
                    kiosk = kiosk,
                    voice = voice,
                    screensaver = screensaver,
                    doorbell = doorbell,
                    nightMode = nightMode,
                    ha = ha,
                    camera = camera,
                )
            },
            noiseConfig = { currentConfig },
            mdns = currentMdns,
        )

    init {
        scope.launch {
            noisePsk.collect { psk ->
                val newConfig =
                    if (psk == null || psk.isEmpty()) {
                        EsphomeNoiseConfig.PlainOnly
                    } else {
                        EsphomeNoiseConfig.NoiseOnly(psk)
                    }
                if (newConfig == currentConfig) {
                    return@collect
                }
                currentConfig = newConfig
                currentMdns =
                    EsphomeMdns(
                        appContext,
                        deviceInfo,
                        noiseEnabled = newConfig is EsphomeNoiseConfig.NoiseOnly,
                    )
                server.swapMdns(currentMdns)
            }
        }
    }

    fun start() = server.start()

    fun stop() = server.stop()
}

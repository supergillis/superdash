package com.superdash.esphome

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class EsphomeBindingsTest {
    @Test
    fun `entity catalog exposes settings and status controls`() {
        val entities =
            esphomeEntities(
                device =
                    EsphomeDeviceMetadata(
                        stableDeviceId = "android-id",
                        deviceModel = "Pixel",
                        deviceManufacturer = "Google",
                        friendlyName = "superdash",
                        appVersionName = "1.0",
                        appVersion = MutableStateFlow("1.0"),
                    ),
                kiosk =
                    EsphomeKioskBindings(
                        keepScreenOn = MutableStateFlow(true),
                        setKeepScreenOn = {},
                        startOnBoot = MutableStateFlow(true),
                        setStartOnBoot = {},
                        launchOnWake = MutableStateFlow(false),
                        setLaunchOnWake = {},
                        screenOn = MutableStateFlow(true),
                        refreshWebView = {},
                        restartApp = {},
                    ),
                voice =
                    EsphomeVoiceBindings(
                        voiceEnabled = MutableStateFlow(false),
                        setVoiceEnabled = {},
                        voiceActive = MutableStateFlow(false),
                        voiceState = MutableStateFlow("Idle"),
                        activeWakeWordState = MutableStateFlow("hey_jarvis"),
                        activeWakeWord = MutableStateFlow("hey_jarvis"),
                        setActiveWakeWord = {},
                        vadSilenceMs = MutableStateFlow(500f),
                        setVadSilenceMs = {},
                        primarySttProvider = MutableStateFlow("ha_assist"),
                        setPrimarySttProvider = {},
                        secondarySttProvider = MutableStateFlow("none"),
                        setSecondarySttProvider = {},
                        voiceAssistProvider = MutableStateFlow("ha_assist"),
                        setVoiceAssistProvider = {},
                    ),
                screensaver =
                    EsphomeScreensaverBindings(
                        inScreensaver = MutableStateFlow(false),
                        dayScreensaverMode = MutableStateFlow("photos"),
                        setDayScreensaverMode = {},
                        nightScreensaverMode = MutableStateFlow("black"),
                        setNightScreensaverMode = {},
                        dayScreensaverModeState = MutableStateFlow("photos"),
                        nightScreensaverModeState = MutableStateFlow("black"),
                        weatherEntityId = MutableStateFlow("weather.home"),
                        mediaLibrarySourceTitle = MutableStateFlow(""),
                        mediaLibraryOrder = MutableStateFlow("shuffle"),
                        setMediaLibraryOrder = {},
                        overlayPosition = MutableStateFlow("bottom_left"),
                        setOverlayPosition = {},
                        idleTimeoutSec = MutableStateFlow(300f),
                        setIdleTimeoutSec = {},
                        pictureSpacingDp = MutableStateFlow(8f),
                        setPictureSpacingDp = {},
                        startScreensaver = {},
                        stopScreensaver = {},
                    ),
                doorbell =
                    EsphomeDoorbellBindings(
                        doorbellEnabled = MutableStateFlow(false),
                        setDoorbellEnabled = {},
                        doorbellRinging = MutableStateFlow(false),
                        doorbellCount = MutableStateFlow(0f),
                        doorbellAutoCloseSec = MutableStateFlow(60f),
                        setDoorbellAutoCloseSec = {},
                    ),
                nightMode =
                    EsphomeNightModeBindings(
                        nightMode = MutableStateFlow(false),
                        setNightMode = {},
                    ),
                ha =
                    EsphomeHaBindings(
                        haEntityCount = MutableStateFlow(0f),
                        haConnectionState = MutableStateFlow("Disconnected"),
                    ),
                camera =
                    EsphomeCameraBindings(
                        cameraEnabled = MutableStateFlow(false),
                        setCameraEnabled = {},
                        motionDetected = MutableStateFlow(false),
                        motionMode = MutableStateFlow("off"),
                        setMotionMode = {},
                        motionSensitivity = MutableStateFlow(50f),
                        setMotionSensitivity = {},
                        motionClearDelaySec = MutableStateFlow(15f),
                        setMotionClearDelaySec = {},
                        wakeOnMotion = MutableStateFlow(false),
                        setWakeOnMotion = {},
                        jpegFrames = MutableStateFlow(ByteArray(0)),
                        latestJpeg = { null },
                    ),
            )

        assertEquals(
            listOf(
                "keep_screen_on",
                "start_on_boot",
                "night_mode",
                "voice_enabled",
                "doorbell_enabled",
                "launch_on_wake",
                "screen_on",
                "in_screensaver",
                "doorbell_ringing",
                "voice_active",
                "ha_entity_count",
                "doorbell_count",
                "ha_connection_state",
                "voice_state",
                "active_wake_word_state",
                "day_screensaver_mode_state",
                "night_screensaver_mode_state",
                "weather_entity_id",
                "media_library_source_title",
                "app_version",
                "vad_silence_ms",
                "idle_timeout_sec",
                "picture_spacing_dp",
                "doorbell_auto_close_sec",
                "day_screensaver_mode",
                "night_screensaver_mode",
                "overlay_position",
                "primary_stt_provider",
                "secondary_stt_provider",
                "voice_assist_provider",
                "active_wake_word",
                "media_library_order",
                "refresh_webview",
                "restart_app",
                "start_screensaver",
                "stop_screensaver",
                "camera_enabled",
                "wake_on_motion",
                "motion",
                "motion_detection_mode",
                "motion_sensitivity",
                "motion_clear_delay_sec",
                "camera",
            ),
            entities.map { entity -> entity.objectId },
        )
        assertEquals(
            listOf("shuffle", "chronological"),
            (entities.single { entity -> entity.objectId == "media_library_order" } as EsphomeEntity.Select).options,
        )
    }

    @Test
    fun `number commands round to integer values before reaching settings`() =
        kotlinx.coroutines.test.runTest {
            val received = mutableListOf<Int>()
            val entity =
                numberEntity(
                    objectId = "idle_timeout_sec",
                    name = "Idle Timeout",
                    state = MutableStateFlow(300f),
                    minValue = 0f,
                    maxValue = 1800f,
                    step = 30f,
                    unitOfMeasurement = "s",
                    onCommand = { value -> received.add(value.roundToInt()) },
                )

            entity.onCommand(599.8f)

            assertEquals(listOf(600), received)
        }

    @Test
    fun `number commands round to configured step before reaching settings`() =
        kotlinx.coroutines.test.runTest {
            val received = mutableListOf<Float>()
            val entity =
                numberEntity(
                    objectId = "idle_timeout_sec",
                    name = "Idle Timeout",
                    state = MutableStateFlow(300f),
                    minValue = 0f,
                    maxValue = 1800f,
                    step = 30f,
                    unitOfMeasurement = "s",
                    onCommand = { value -> received.add(value) },
                )

            entity.onCommand(607.2f)

            assertEquals(listOf(600f), received)
        }

    @Test
    fun `select command ignores values outside option list`() =
        kotlinx.coroutines.test.runTest {
            val received = mutableListOf<String>()
            val entity =
                selectEntity(
                    objectId = "day_screensaver_mode",
                    name = "Day Screensaver Mode",
                    state = MutableStateFlow("photos"),
                    options = listOf("off", "photos"),
                    onCommand = { value -> received.add(value) },
                )

            entity.onCommand("invalid")

            assertEquals(emptyList<String>(), received)
        }
}

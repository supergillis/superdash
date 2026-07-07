package com.superdash.esphome

import kotlinx.coroutines.flow.Flow

/** Stable device identity and app metadata used to advertise the ESPHome node. */
data class EsphomeDeviceMetadata(
    val stableDeviceId: String,
    val deviceModel: String,
    val deviceManufacturer: String,
    val friendlyName: String,
    val appVersionName: String,
    val appVersion: Flow<String>,
)

/** Kiosk-level switches, screen state, and host commands. */
data class EsphomeKioskBindings(
    val keepScreenOn: Flow<Boolean>,
    val setKeepScreenOn: suspend (Boolean) -> Unit,
    val startOnBoot: Flow<Boolean>,
    val setStartOnBoot: suspend (Boolean) -> Unit,
    val launchOnWake: Flow<Boolean>,
    val setLaunchOnWake: suspend (Boolean) -> Unit,
    val screenOn: Flow<Boolean>,
    val refreshWebView: suspend () -> Unit,
    val restartApp: suspend () -> Unit,
)

/** Voice pipeline enable, status, STT/assist provider selection, and wake word. */
data class EsphomeVoiceBindings(
    val voiceEnabled: Flow<Boolean>,
    val setVoiceEnabled: suspend (Boolean) -> Unit,
    val voiceActive: Flow<Boolean>,
    val voiceState: Flow<String>,
    val activeWakeWordState: Flow<String>,
    val activeWakeWord: Flow<String>,
    val setActiveWakeWord: suspend (String) -> Unit,
    val vadSilenceMs: Flow<Float>,
    val setVadSilenceMs: suspend (Float) -> Unit,
    val primarySttProvider: Flow<String>,
    val setPrimarySttProvider: suspend (String) -> Unit,
    val secondarySttProvider: Flow<String>,
    val setSecondarySttProvider: suspend (String) -> Unit,
    val voiceAssistProvider: Flow<String>,
    val setVoiceAssistProvider: suspend (String) -> Unit,
)

/** Screensaver mode, idle controls, picture spacing, weather, media library, and start/stop commands. */
data class EsphomeScreensaverBindings(
    val inScreensaver: Flow<Boolean>,
    val dayScreensaverMode: Flow<String>,
    val setDayScreensaverMode: suspend (String) -> Unit,
    val nightScreensaverMode: Flow<String>,
    val setNightScreensaverMode: suspend (String) -> Unit,
    val dayScreensaverModeState: Flow<String>,
    val nightScreensaverModeState: Flow<String>,
    val weatherEntityId: Flow<String>,
    val mediaLibrarySourceTitle: Flow<String>,
    val mediaLibraryOrder: Flow<String>,
    val setMediaLibraryOrder: suspend (String) -> Unit,
    val overlayPosition: Flow<String>,
    val setOverlayPosition: suspend (String) -> Unit,
    val idleTimeoutSec: Flow<Float>,
    val setIdleTimeoutSec: suspend (Float) -> Unit,
    val pictureSpacingDp: Flow<Float>,
    val setPictureSpacingDp: suspend (Float) -> Unit,
    val startScreensaver: suspend () -> Unit,
    val stopScreensaver: suspend () -> Unit,
)

/** Doorbell enable, ring status, configured count, and auto-close timing. */
data class EsphomeDoorbellBindings(
    val doorbellEnabled: Flow<Boolean>,
    val setDoorbellEnabled: suspend (Boolean) -> Unit,
    val doorbellRinging: Flow<Boolean>,
    val doorbellCount: Flow<Float>,
    val doorbellAutoCloseSec: Flow<Float>,
    val setDoorbellAutoCloseSec: suspend (Float) -> Unit,
)

/** Manual night-mode override exposed as an ESPHome switch. */
data class EsphomeNightModeBindings(
    val nightMode: Flow<Boolean>,
    val setNightMode: suspend (Boolean) -> Unit,
)

/** Tablet camera: enable switch, motion sensor and tuning, wake-on-motion,
 *  and JPEG frame sources for the ESPHome camera entity. */
data class EsphomeCameraBindings(
    val cameraEnabled: Flow<Boolean>,
    val setCameraEnabled: suspend (Boolean) -> Unit,
    val motionDetected: Flow<Boolean>,
    val motionMode: Flow<String>,
    val setMotionMode: suspend (String) -> Unit,
    val motionSensitivity: Flow<Float>,
    val setMotionSensitivity: suspend (Float) -> Unit,
    val motionClearDelaySec: Flow<Float>,
    val setMotionClearDelaySec: suspend (Float) -> Unit,
    val wakeOnMotion: Flow<Boolean>,
    val setWakeOnMotion: suspend (Boolean) -> Unit,
    val jpegFrames: Flow<ByteArray>,
    val latestJpeg: suspend () -> ByteArray?,
)

/** Home Assistant connectivity telemetry. */
data class EsphomeHaBindings(
    val haEntityCount: Flow<Float>,
    val haConnectionState: Flow<String>,
)

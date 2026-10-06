package com.superdash

import android.app.Application
import com.superdash.camera.CameraController
import com.superdash.camera.CameraSettings
import com.superdash.device.DeviceInfo
import com.superdash.device.ScreenStateProvider
import com.superdash.esphome.EsphomeBindings
import com.superdash.esphome.EsphomeCameraBindings
import com.superdash.esphome.EsphomeDeviceMetadata
import com.superdash.esphome.EsphomeFeedBindings
import com.superdash.esphome.EsphomeHaBindings
import com.superdash.esphome.EsphomeKioskBindings
import com.superdash.esphome.EsphomeNightModeBindings
import com.superdash.esphome.EsphomeScreensaverBindings
import com.superdash.esphome.EsphomeVoiceBindings
import com.superdash.feed.FeedOverlayController
import com.superdash.feed.FeedSettings
import com.superdash.feed.FeedState
import com.superdash.ha.HaWebSocketClient
import com.superdash.kiosk.KioskSettings
import com.superdash.kiosk.bus.ActivityCommand
import com.superdash.kiosk.bus.ActivityCommandQueue
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.screensaver.MediaLibraryOrder
import com.superdash.screensaver.ScreensaverIdleController
import com.superdash.screensaver.ScreensaverSettings
import com.superdash.sleep.SleepController
import com.superdash.voice.VoiceSettings
import com.superdash.voice.pipeline.VoicePipelineCoordinator
import com.superdash.voice.pipeline.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class EsphomeSubgraph(
    application: Application,
    scope: CoroutineScope,
    feedSettings: FeedSettings,
    screensaverSettings: ScreensaverSettings,
    voiceSettings: VoiceSettings,
    kioskSettings: KioskSettings,
    eventBus: KioskEventBus,
    activityCommandQueue: ActivityCommandQueue,
    deviceInfo: DeviceInfo,
    screenStateProvider: ScreenStateProvider,
    idleController: ScreensaverIdleController,
    sleepController: SleepController,
    feedOverlayController: FeedOverlayController,
    voiceCoordinator: VoicePipelineCoordinator,
    haClient: HaWebSocketClient,
    noisePsk: Flow<ByteArray?> = flowOf(null),
    cameraSettings: CameraSettings,
    cameraController: CameraController,
) {
    private val deviceMetadata: EsphomeDeviceMetadata =
        EsphomeDeviceMetadata(
            stableDeviceId = deviceInfo.androidId,
            deviceModel = deviceInfo.deviceModel,
            deviceManufacturer = deviceInfo.deviceManufacturer,
            friendlyName = deviceInfo.deviceName,
            appVersionName = deviceInfo.appVersionName,
            appVersion = flowOf(deviceInfo.appVersionName),
        )

    private val kioskBindings: EsphomeKioskBindings =
        EsphomeKioskBindings(
            keepScreenOn = kioskSettings.keepScreenOn,
            setKeepScreenOn = { value -> kioskSettings.setKeepScreenOn(value) },
            startOnBoot = kioskSettings.startOnBoot,
            setStartOnBoot = { value -> kioskSettings.setStartOnBoot(value) },
            launchOnWake = kioskSettings.launchOnWake,
            setLaunchOnWake = { value -> kioskSettings.setLaunchOnWake(value) },
            screenOn = screenStateProvider.state,
            refreshWebView = { activityCommandQueue.submit(ActivityCommand.RefreshWebView) },
            restartApp = { activityCommandQueue.submit(ActivityCommand.RestartApp) },
        )

    private val voiceBindings: EsphomeVoiceBindings =
        EsphomeVoiceBindings(
            voiceEnabled = voiceSettings.enabled,
            setVoiceEnabled = { value -> voiceSettings.setEnabled(value) },
            voiceActive =
                voiceCoordinator.state
                    .map { it !is VoiceState.Idle }
                    .distinctUntilChanged(),
            voiceState =
                voiceCoordinator.state
                    .map { state -> state::class.simpleName.orEmpty() }
                    .distinctUntilChanged(),
            activeWakeWordState = voiceSettings.activeWakeWord,
            activeWakeWord = voiceSettings.activeWakeWord,
            setActiveWakeWord = { value -> voiceSettings.setActiveWakeWord(value) },
            vadSilenceMs =
                voiceSettings.vadSilenceMs
                    .map { value -> value.toFloat() }
                    .distinctUntilChanged(),
            setVadSilenceMs = { value -> voiceSettings.setVadSilenceMs(value.toInt()) },
            primarySttProvider = voiceSettings.primarySttProvider,
            setPrimarySttProvider = { value -> voiceSettings.setPrimarySttProvider(value) },
            secondarySttProvider = voiceSettings.secondarySttProvider,
            setSecondarySttProvider = { value -> voiceSettings.setSecondarySttProvider(value) },
            voiceAssistProvider = voiceSettings.assistProvider,
            setVoiceAssistProvider = { value -> voiceSettings.setAssistProvider(value) },
        )

    private val screensaverBindings: EsphomeScreensaverBindings =
        EsphomeScreensaverBindings(
            inScreensaver = idleController.isIdle,
            dayScreensaverMode = screensaverSettings.dayMode,
            setDayScreensaverMode = { value -> screensaverSettings.setDayMode(value) },
            nightScreensaverMode = screensaverSettings.nightMode,
            setNightScreensaverMode = { value -> screensaverSettings.setNightMode(value) },
            dayScreensaverModeState = screensaverSettings.dayMode,
            nightScreensaverModeState = screensaverSettings.nightMode,
            weatherEntityId = screensaverSettings.weatherEntityId,
            mediaLibrarySourceTitle =
                screensaverSettings.mediaLibrarySourceTitle
                    .map { value -> value.orEmpty() }
                    .distinctUntilChanged(),
            mediaLibraryOrder =
                screensaverSettings.mediaLibraryOrder
                    .map { value -> MediaLibraryOrder.fromKey(value).key }
                    .distinctUntilChanged(),
            setMediaLibraryOrder = { value ->
                screensaverSettings.setMediaLibraryOrder(MediaLibraryOrder.fromKey(value).key)
            },
            overlayPosition = screensaverSettings.overlayPosition,
            setOverlayPosition = { value -> screensaverSettings.setOverlayPosition(value) },
            idleTimeoutSec =
                screensaverSettings.idleTimeoutSec
                    .map { value -> value.toFloat() }
                    .distinctUntilChanged(),
            setIdleTimeoutSec = { value -> screensaverSettings.setIdleTimeoutSec(value.toInt()) },
            pictureSpacingDp =
                screensaverSettings.pictureSpacingDp
                    .map { value -> value.toFloat() }
                    .distinctUntilChanged(),
            setPictureSpacingDp = { value -> screensaverSettings.setPictureSpacingDp(value.toInt()) },
            startScreensaver = { idleController.forceIdle() },
            stopScreensaver = { eventBus.emit(KioskEvent.UserTouched) },
        )

    private val feedBindings: EsphomeFeedBindings =
        EsphomeFeedBindings(
            feedEnabled = feedSettings.enabled,
            setFeedEnabled = { value -> feedSettings.setEnabled(value) },
            feedRinging =
                feedOverlayController.state
                    .map { it is FeedState.Showing }
                    .distinctUntilChanged(),
            feedCount =
                feedSettings.feeds
                    .map { configs -> configs.size.toFloat() }
                    .distinctUntilChanged(),
        )

    private val nightModeBindings: EsphomeNightModeBindings =
        EsphomeNightModeBindings(
            nightMode = sleepController.nightModeActive,
            setNightMode = { value -> sleepController.setNightModeActive(value) },
        )

    private val haBindings: EsphomeHaBindings =
        EsphomeHaBindings(
            haEntityCount =
                haClient.entities
                    .map { entities -> entities.size.toFloat() }
                    .distinctUntilChanged(),
            haConnectionState =
                haClient.state
                    .map { state -> state::class.simpleName.orEmpty() }
                    .distinctUntilChanged(),
        )

    private val cameraBindings: EsphomeCameraBindings =
        EsphomeCameraBindings(
            cameraEnabled = cameraSettings.enabled,
            setCameraEnabled = { value -> cameraSettings.setEnabled(value) },
            allowRemoteEnable = cameraSettings.allowRemoteEnable,
            motionDetected = cameraController.motionActive,
            motionMode = cameraController.activeMotionMode,
            setMotionMode = { value -> cameraSettings.setMotionMode(value) },
            motionSensitivity =
                cameraSettings.motionSensitivity
                    .map { value -> value.toFloat() }
                    .distinctUntilChanged(),
            setMotionSensitivity = { value -> cameraSettings.setMotionSensitivity(value.toInt()) },
            motionClearDelaySec =
                cameraSettings.motionClearDelaySec
                    .map { value -> value.toFloat() }
                    .distinctUntilChanged(),
            setMotionClearDelaySec = { value -> cameraSettings.setMotionClearDelaySec(value.toInt()) },
            maxFps =
                cameraSettings.maxFps
                    .map { value -> value.toFloat() }
                    .distinctUntilChanged(),
            setMaxFps = { value -> cameraSettings.setMaxFps(value.toInt()) },
            wakeOnMotion = cameraSettings.wakeOnMotion,
            setWakeOnMotion = { value -> cameraSettings.setWakeOnMotion(value) },
            cameraStatus =
                cameraController.availability
                    .map { it::class.simpleName.orEmpty() }
                    .distinctUntilChanged(),
            jpegFrames = cameraController.jpegFrames,
            latestJpeg = { cameraController.latestJpeg() },
        )

    val bindings: EsphomeBindings =
        EsphomeBindings(
            appContext = application.applicationContext,
            scope = scope,
            enabled = kioskSettings.esphomeEnabled,
            noisePsk = noisePsk,
            device = deviceMetadata,
            kiosk = kioskBindings,
            voice = voiceBindings,
            screensaver = screensaverBindings,
            feed = feedBindings,
            nightMode = nightModeBindings,
            ha = haBindings,
            camera = cameraBindings,
        )
}

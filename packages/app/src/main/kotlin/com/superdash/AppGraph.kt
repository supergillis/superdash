package com.superdash

import android.app.Application
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.superdash.camera.CameraController
import com.superdash.camera.CameraService
import com.superdash.camera.CameraServiceController
import com.superdash.camera.CameraSettings
import com.superdash.camera.CameraXPipeline
import com.superdash.camera.FrameDiffMotionDetector
import com.superdash.camera.PersonMotionDetector
import com.superdash.core.persistence.DataStoreKeyValueStore
import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.resources.AndroidStringProvider
import com.superdash.core.resources.StringProvider
import com.superdash.device.DeviceInfo
import com.superdash.device.ScreenStateProvider
import com.superdash.doorbell.DoorbellOverlayController
import com.superdash.doorbell.DoorbellSettings
import com.superdash.doorbell.DoorbellWatcher
import com.superdash.esphome.EsphomeBindings
import com.superdash.esphome.glue.EsphomePskStore
import com.superdash.ha.HaAssistClient
import com.superdash.ha.HaConnectivityController
import com.superdash.ha.HaMediaSourceClient
import com.superdash.ha.HaServiceCallClient
import com.superdash.ha.HaTokenProvider
import com.superdash.ha.HaTokenStore
import com.superdash.ha.HaWebSocketClient
import com.superdash.ha.media.CameraStreamSource
import com.superdash.ha.security.AeadEncryption
import com.superdash.ha.security.KeystoreKeyProvider
import com.superdash.immich.ImmichApiClient
import com.superdash.immich.ImmichSettings
import com.superdash.immich.okhttp.ImmichAuthInterceptor
import com.superdash.kiosk.KioskSettings
import com.superdash.kiosk.SidebarSettings
import com.superdash.kiosk.bus.ActivityCommandQueue
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.locale.LocaleController
import com.superdash.screensaver.ScreensaverIdleController
import com.superdash.screensaver.ScreensaverSettings
import com.superdash.settings.AeadSecretString
import com.superdash.settings.SettingsRepository
import com.superdash.settings.SettingsRepositoryCameraSettings
import com.superdash.settings.SettingsRepositoryDoorbellSettings
import com.superdash.settings.SettingsRepositoryImmichSettings
import com.superdash.settings.SettingsRepositoryKioskSettings
import com.superdash.settings.SettingsRepositoryScreensaverSettings
import com.superdash.settings.SettingsRepositorySidebarSettings
import com.superdash.settings.SettingsRepositoryVoiceSettings
import com.superdash.sleep.SleepController
import com.superdash.voice.VoiceSettings
import com.superdash.voice.action.executors.HaTtsPlayer
import com.superdash.voice.models.VoiceModelCatalog
import com.superdash.voice.models.VoiceModelDownloader
import com.superdash.voice.models.VoiceModelRepository
import com.superdash.voice.models.VoiceModelSettingsCommands
import com.superdash.voice.models.VoiceModelStore
import com.superdash.voice.pipeline.VoiceCaptureLoop
import com.superdash.voice.pipeline.VoicePipelineCoordinator
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class AppGraph(
    private val application: Application,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository = SettingsRepository(application)

    val keyValueStore: KeyValueStore = DataStoreKeyValueStore(settings.dataStore)

    val localeController: LocaleController = LocaleController()

    val strings: StringProvider = AndroidStringProvider(application)

    val doorbellSettings: DoorbellSettings = SettingsRepositoryDoorbellSettings(keyValueStore)

    val cameraSettings: CameraSettings = SettingsRepositoryCameraSettings(keyValueStore)

    val immichSettings: ImmichSettings =
        SettingsRepositoryImmichSettings(
            store = keyValueStore,
            secret = AeadSecretString(AeadEncryption(KeystoreKeyProvider(application))),
        )

    val screensaverSettings: ScreensaverSettings = SettingsRepositoryScreensaverSettings(keyValueStore)

    val voiceSettings: VoiceSettings = SettingsRepositoryVoiceSettings(keyValueStore)

    val kioskSettings: KioskSettings = SettingsRepositoryKioskSettings(keyValueStore)

    val sidebarSettings: SidebarSettings = SettingsRepositorySidebarSettings(keyValueStore)

    val ha: HaSubgraph = HaSubgraph(application, scope, settings)

    val immich: ImmichSubgraph = ImmichSubgraph(scope, immichSettings, ha.httpClient, application.applicationContext)

    private val esphomePskStore = EsphomePskStore(application)

    val esphomePsk: EsphomePskStore get() = esphomePskStore

    val httpClient: HttpClient get() = ha.httpClient
    val tokenStore: HaTokenStore get() = ha.tokenStore
    val haUrlFlow: StateFlow<String?> get() = ha.haUrlFlow
    val tokenProvider: HaTokenProvider get() = ha.tokenProvider
    val haClient: HaWebSocketClient get() = ha.client
    val assistClient: HaAssistClient get() = ha.assistClient
    val cameraStreamSource: CameraStreamSource get() = ha.cameraStreamSource
    val haMediaSource: HaMediaSourceClient get() = ha.mediaSource
    val haServiceCalls: HaServiceCallClient get() = ha.serviceCalls
    val immichClient: StateFlow<ImmichApiClient?> get() = immich.client

    val eventBus: KioskEventBus = KioskEventBus()

    private val cameraSensitivity: StateFlow<Int> =
        cameraSettings.motionSensitivity.stateIn(scope, SharingStarted.Eagerly, 50)

    val cameraController: CameraController =
        CameraController(
            pipeline = CameraXPipeline(application.applicationContext),
            settings = cameraSettings,
            detectorFactories =
                mapOf(
                    "motion" to { FrameDiffMotionDetector(sensitivityPercent = { cameraSensitivity.value }) },
                    "person" to { PersonMotionDetector() },
                ),
            scope = scope,
        )

    val activityCommandQueue: ActivityCommandQueue = ActivityCommandQueue()

    val voiceModels: VoiceModelRepository by lazy {
        VoiceModelRepository(
            catalog = VoiceModelCatalog.models,
            store = VoiceModelStore(application.filesDir),
            downloader = VoiceModelDownloader(httpClient, application.filesDir),
            selectedSttModelIds = voiceSettings.selectedSttModelId,
            selectedIntentEmbeddingModelIds = voiceSettings.selectedIntentEmbeddingModelId,
            commands =
                object : VoiceModelSettingsCommands {
                    override suspend fun setSelectedSttModelId(value: String) {
                        voiceSettings.setSelectedSttModelId(value)
                    }

                    override suspend fun setSelectedIntentEmbeddingModelId(value: String) {
                        voiceSettings.setSelectedIntentEmbeddingModelId(value)
                    }
                },
            assetOpener = { path -> application.assets.open(path) },
            scope = scope,
        )
    }

    val voice: VoiceSubgraph = VoiceSubgraph(application, voiceSettings, eventBus, ha, voiceModels.state, scope)

    val ttsPlayer: HaTtsPlayer get() = voice.ttsPlayer
    val voiceCoordinator: VoicePipelineCoordinator get() = voice.coordinator
    val voiceCaptureLoop: VoiceCaptureLoop get() = voice.captureLoop

    private val imageOkHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(ImmichAuthInterceptor(immich.serverOrigin, immich.apiKey))
            .build()

    val imageLoader: ImageLoader =
        ImageLoader
            .Builder(application)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
            }.crossfade(true)
            .build()

    val idleController: ScreensaverIdleController =
        ScreensaverIdleController(
            timeoutSecondsFlow = screensaverSettings.idleTimeoutSec,
            scope = scope,
        )

    val sleepController: SleepController =
        SleepController(
            nightModeActiveFlow = screensaverSettings.nightModeActive,
            setNightModeActive = { value -> screensaverSettings.setNightModeActive(value) },
            bus = eventBus,
            idleController = idleController,
            scope = scope,
        )

    val doorbellWatcher: DoorbellWatcher =
        DoorbellWatcher(
            scope = scope,
            doorbellsFlow = doorbellSettings.doorbells,
            enabledFlow = doorbellSettings.enabled,
            observeEntity = { entityId -> haClient.observeEntity(entityId) },
            bus = eventBus,
        )

    val doorbellOverlayController: DoorbellOverlayController =
        DoorbellOverlayController(
            scope = scope,
            bus = eventBus,
            doorbellsFlow = doorbellSettings.doorbells,
        )

    val deviceInfo: DeviceInfo = DeviceInfo(application)

    val screenStateProvider: ScreenStateProvider =
        ScreenStateProvider(application.applicationContext)

    @Suppress("unused")
    private val cameraServiceController: CameraServiceController =
        CameraServiceController(
            enabled = cameraSettings.enabled,
            screenOn = screenStateProvider.state,
            scope = scope,
            start = { CameraService.start(application.applicationContext) },
            stop = { CameraService.stop(application.applicationContext) },
        )

    val haConnectivityController: HaConnectivityController =
        HaConnectivityController(
            context = application,
            haClient = haClient,
            tokenStore = tokenStore,
        )

    val esphomeSubgraph: EsphomeSubgraph =
        EsphomeSubgraph(
            application = application,
            scope = scope,
            doorbellSettings = doorbellSettings,
            screensaverSettings = screensaverSettings,
            voiceSettings = voiceSettings,
            kioskSettings = kioskSettings,
            eventBus = eventBus,
            activityCommandQueue = activityCommandQueue,
            deviceInfo = deviceInfo,
            screenStateProvider = screenStateProvider,
            idleController = idleController,
            sleepController = sleepController,
            doorbellOverlayController = doorbellOverlayController,
            voiceCoordinator = voiceCoordinator,
            haClient = haClient,
            noisePsk = esphomePskStore.psk,
            cameraSettings = cameraSettings,
            cameraController = cameraController,
        )

    val esphome: EsphomeBindings get() = esphomeSubgraph.bindings

    init {
        scope.launch {
            cameraController.motionActive
                .filter { it }
                .collect {
                    if (cameraSettings.wakeOnMotion.first()) {
                        eventBus.emit(KioskEvent.UserTouched)
                    }
                }
        }
    }

    fun shutdown() {
        // closeAll() is suspending; this only runs on Application.onTerminate
        // (emulator-only, process death). See SuperdashApp.onTerminate for why
        // blocking here is acceptable. On real devices this never runs.
        runCatching { kotlinx.coroutines.runBlocking { voice.providerRegistry.closeAll() } }
        runCatching { ttsPlayer.close() }
        runCatching { haConnectivityController.stop() }
    }
}

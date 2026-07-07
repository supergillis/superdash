package com.superdash.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.superdash.AppGraph
import com.superdash.MainActivity
import com.superdash.R
import com.superdash.SuperdashApp
import com.superdash.core.locale.SupportedLanguage
import com.superdash.core.util.UrlNormalizer
import com.superdash.doorbell.DoorbellConfig
import com.superdash.immich.ImmichApiClient
import com.superdash.immich.ImmichProbeResult
import com.superdash.kiosk.BatteryOptimizationPrompt
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarShortcut
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.ui.KioskOverlayState
import com.superdash.kiosk.ui.KioskOverlays
import com.superdash.kiosk.ui.debug.WsDebugActivity
import com.superdash.screensaver.ScreensaverHost
import com.superdash.screensaver.picker.MediaSourcePickerDialog
import com.superdash.theme.SuperdashTheme
import com.superdash.voice.pipeline.VoiceResponseMode
import com.superdash.voice.pipeline.VoiceSttProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class SettingsActions(
    val general: GeneralSettingsActions,
    val connection: ConnectionSettingsActions,
    val device: DeviceSettingsActions,
    val voice: VoiceSettingsActions,
    val doorbell: DoorbellSettingsActions,
    val camera: CameraSettingsActions,
    val esphome: EsphomeSettingsActions,
    val screensaver: ScreensaverSettingsActions,
    val immich: ImmichSettingsActions,
    val sidebar: SidebarSettingsActions,
    val admin: AdminSettingsActions,
    val onBack: () -> Unit,
)

@Immutable
data class GeneralSettingsActions(
    val onSelectLanguage: (SupportedLanguage?) -> Unit,
)

@Immutable
data class ConnectionSettingsActions(
    val onHaUrlChange: (String) -> Unit,
    val onTestConnection: suspend (String) -> Boolean,
    val onReauthenticate: () -> Unit,
    val onDashboardPathChange: (String) -> Unit,
)

@Immutable
data class DeviceSettingsActions(
    val onKeepScreenOnChange: (Boolean) -> Unit,
    val onStartOnBootChange: (Boolean) -> Unit,
)

@Immutable
data class VoiceSettingsActions(
    val onRequestVoiceEnable: () -> Unit,
    val onVoiceDisable: () -> Unit,
    val onActiveWakeWordChange: (String) -> Unit,
    val onVoiceAssistProviderChange: (VoiceSttProvider) -> Unit,
    val onPrimarySttProviderChange: (VoiceSttProvider) -> Unit,
    val onSecondarySttProviderChange: (VoiceSttProvider) -> Unit,
    val onSelectedSttModelChange: (String) -> Unit,
    val onSelectedIntentEmbeddingModelChange: (String) -> Unit,
    val onLocalIntentRecognizerEnabledChange: (Boolean) -> Unit,
    val onDownloadVoiceModel: (String) -> Unit,
    val onDeleteVoiceModel: (String) -> Unit,
    val onVoiceResponseModeChange: (VoiceResponseMode) -> Unit,
    val onCommandRecordingEnabledChange: (Boolean) -> Unit,
    val onCommandRecordingRetentionChange: (Int) -> Unit,
    val onClearCommandRecordings: () -> Unit,
    val onVadSilenceMsChange: (Int) -> Unit,
)

@Immutable
data class ScreensaverSettingsActions(
    val onDayScreensaverModeChange: (String) -> Unit,
    val onNightScreensaverModeChange: (String) -> Unit,
    val onIdleTimeoutSecChange: (Int) -> Unit,
    val onWeatherEntityIdChange: (String) -> Unit,
    val onCalendarEntityIdChange: (String) -> Unit,
    val onPowerUsageEntityIdChange: (String) -> Unit,
    val onSolarPowerEntityIdChange: (String) -> Unit,
    val onGridPowerEntityIdChange: (String) -> Unit,
    val onOverlayPositionChange: (String) -> Unit,
    val onPictureSpacingDpChange: (Int) -> Unit,
    val onMediaLibrarySourceChange: (id: String?, title: String?) -> Unit,
    val onMediaLibraryOrderChange: (String) -> Unit,
    val onTestScreensaver: () -> Unit,
)

@Immutable
data class ImmichSettingsActions(
    val onImmichUrlChange: (String) -> Unit,
    val onImmichApiKeyChange: (String) -> Unit,
    val onImmichAlbumChange: (String) -> Unit,
    val onImmichCatalogTtlHoursChange: (Int) -> Unit,
    val onRefreshImmichCatalog: suspend () -> String,
    val onTestImmich: suspend (url: String, apiKey: String, album: String) -> String,
)

@Immutable
data class DoorbellSettingsActions(
    val onDoorbellEnabledChange: (Boolean) -> Unit,
    val onDoorbellAutoCloseSecChange: (Int) -> Unit,
    val onUpsertDoorbell: (DoorbellConfig) -> Unit,
    val onRemoveDoorbell: (String) -> Unit,
    val onTestDoorbell: (DoorbellConfig) -> Unit,
)

@Immutable
data class CameraSettingsActions(
    val onRequestCameraEnable: () -> Unit,
    val onCameraDisable: () -> Unit,
    val onFacingChange: (String) -> Unit,
    val onResolutionChange: (String) -> Unit,
    val onMotionModeChange: (String) -> Unit,
    val onMotionSensitivityChange: (Int) -> Unit,
    val onMotionClearDelayChange: (Int) -> Unit,
    val onWakeOnMotionChange: (Boolean) -> Unit,
)

@Immutable
data class EsphomeSettingsActions(
    val onEsphomeEnabledChange: (Boolean) -> Unit,
    val onSavePskBase64: (String) -> Boolean,
    val onClearPsk: () -> Unit,
)

@Immutable
data class AdminSettingsActions(
    val onBatteryHelp: () -> Unit,
    val onOpenWsDebug: () -> Unit,
)

@Immutable
data class SidebarSettingsActions(
    val onPositionChange: (SidebarPosition) -> Unit,
    val onPinnedChange: (Boolean) -> Unit,
    val onShowLabelsChange: (Boolean) -> Unit,
    val onEdgeHandleChange: (Boolean) -> Unit,
    val onShortcutsChange: (List<SidebarShortcut>) -> Unit,
)

class SettingsActivity : AppCompatActivity() {
    private lateinit var settingsViewModel: SettingsViewModel

    private val micPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            settingsViewModel.setVoiceEnabled(granted)
        }

    fun requestMicAndEnableVoice() {
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            settingsViewModel.setVoiceEnabled(true)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            settingsViewModel.setCameraEnabled(granted)
        }

    fun requestCameraAndEnable() {
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            settingsViewModel.setCameraEnabled(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SuperdashApp
        val graph = app.graph
        settingsViewModel =
            ViewModelProvider(this, SettingsViewModel.Factory(graph))[SettingsViewModel::class.java]
        val haMediaSource = graph.haMediaSource
        val screensaverContent: @Composable () -> Unit = {
            ScreensaverHost(
                settings = graph.screensaverSettings,
                immichAlbumFlow = graph.immichSettings.album,
                immichCatalogTtlHoursFlow = graph.immichSettings.catalogTtlHours,
                haClient = graph.haClient,
                haMediaSource = haMediaSource,
                immichClient = graph.immichClient,
                immichCatalogStore = graph.immich.catalogStore,
                onImmichSourceCreated = { graph.immich.registerSource(it) },
                imageLoader = graph.imageLoader,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val mediaSourcePicker: @Composable (
            onConfirm: (id: String, title: String) -> Unit,
            onDismiss: () -> Unit,
        ) -> Unit = { onConfirm, onDismiss ->
            MediaSourcePickerDialog(
                browse = { parentId -> haMediaSource.browseMedia(parentId) },
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        }
        val bearerTokenProvider: suspend () -> String? = { graph.tokenStore.loadAccessToken() }
        val fetchHlsUrl: suspend (String) -> String = { entity -> graph.cameraStreamSource.fetchHlsUrl(entity) }
        val onCloseDoorbell: () -> Unit = { graph.doorbellOverlayController.close() }
        val onTapScreensaver: () -> Unit = { graph.eventBus.emit(KioskEvent.UserTouched) }
        val onTestImmich: suspend (String, String, String) -> String = { url, apiKey, album ->
            runImmichTest(this@SettingsActivity, graph.httpClient, url, apiKey, album)
        }
        setContent {
            SuperdashTheme {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    screensaverContent = screensaverContent,
                    mediaSourcePicker = mediaSourcePicker,
                    bearerTokenProvider = bearerTokenProvider,
                    fetchHlsUrl = fetchHlsUrl,
                    onCloseDoorbell = onCloseDoorbell,
                    onTapScreensaver = onTapScreensaver,
                    onTestImmich = onTestImmich,
                    onTestConnection = { url -> testConnection(url) },
                    onReauthenticate = { reauthenticateHa(graph) },
                    onBatteryHelp = { BatteryOptimizationPrompt.openSettingsForUser(this@SettingsActivity) },
                    onOpenWsDebug = { startActivity(Intent(this@SettingsActivity, WsDebugActivity::class.java)) },
                    onRequestVoiceEnable = { requestMicAndEnableVoice() },
                    onRequestCameraEnable = { requestCameraAndEnable() },
                    onBack = { finish() },
                    // Don't finish(). Overlay renders above Settings, so closing
                    // the overlay returns the user to where they triggered Test.
                    onTestDoorbell = { config -> graph.doorbellOverlayController.show(config) },
                    onTestScreensaver = { graph.idleController.forceIdle() },
                )
            }
        }
    }

    private suspend fun testConnection(url: String): Boolean =
        withContext(Dispatchers.IO) {
            // Simple HEAD/GET against /manifest.json with a 5s timeout.
            // Dispatchers.IO is required: HttpURLConnection blocks; rememberCoroutineScope's
            // default Main dispatcher would trigger NetworkOnMainThreadException.
            val normalized = UrlNormalizer.normalize(url) ?: return@withContext false
            runCatching {
                val manifestUrl = java.net.URL("$normalized/manifest.json")
                val conn = manifestUrl.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.requestMethod = "GET"
                val ok = conn.responseCode in 200..299
                conn.disconnect()
                ok
            }.getOrDefault(false)
        }

    private fun reauthenticateHa(graph: AppGraph) {
        lifecycleScope.launch {
            graph.tokenStore.clear()
            startActivity(
                Intent(this@SettingsActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
    }
}

@Composable
private fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    screensaverContent: @Composable () -> Unit,
    mediaSourcePicker: @Composable (
        onConfirm: (id: String, title: String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    bearerTokenProvider: suspend () -> String?,
    fetchHlsUrl: suspend (String) -> String,
    onCloseDoorbell: () -> Unit,
    onTapScreensaver: () -> Unit,
    onTestImmich: suspend (url: String, apiKey: String, album: String) -> String,
    onTestConnection: suspend (String) -> Boolean,
    onReauthenticate: () -> Unit,
    onBatteryHelp: () -> Unit,
    onOpenWsDebug: () -> Unit,
    onRequestVoiceEnable: () -> Unit,
    onRequestCameraEnable: () -> Unit,
    onBack: () -> Unit,
    onTestDoorbell: (DoorbellConfig) -> Unit,
    onTestScreensaver: () -> Unit,
) {
    val state by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val actions =
        remember(
            onTestConnection,
            onReauthenticate,
            onBatteryHelp,
            onOpenWsDebug,
            onRequestVoiceEnable,
            onRequestCameraEnable,
            onBack,
            onTestDoorbell,
            onTestScreensaver,
            onTestImmich,
            settingsViewModel,
        ) {
            SettingsActions(
                general =
                    GeneralSettingsActions(
                        onSelectLanguage = settingsViewModel::setLanguage,
                    ),
                connection =
                    ConnectionSettingsActions(
                        onHaUrlChange = settingsViewModel::setHaUrl,
                        onDashboardPathChange = settingsViewModel::setDashboardPath,
                        onTestConnection = onTestConnection,
                        onReauthenticate = onReauthenticate,
                    ),
                device =
                    DeviceSettingsActions(
                        onKeepScreenOnChange = settingsViewModel::setKeepScreenOn,
                        onStartOnBootChange = settingsViewModel::setStartOnBoot,
                    ),
                voice =
                    VoiceSettingsActions(
                        onRequestVoiceEnable = onRequestVoiceEnable,
                        onVoiceDisable = { settingsViewModel.setVoiceEnabled(false) },
                        onActiveWakeWordChange = settingsViewModel::setActiveWakeWord,
                        onVoiceAssistProviderChange = settingsViewModel::setVoiceAssistProvider,
                        onPrimarySttProviderChange = settingsViewModel::setPrimarySttProvider,
                        onSecondarySttProviderChange = settingsViewModel::setSecondarySttProvider,
                        onSelectedSttModelChange = settingsViewModel::setSelectedSttModelId,
                        onSelectedIntentEmbeddingModelChange = settingsViewModel::setSelectedIntentEmbeddingModelId,
                        onLocalIntentRecognizerEnabledChange = settingsViewModel::setLocalIntentRecognizerEnabled,
                        onDownloadVoiceModel = settingsViewModel::downloadVoiceModel,
                        onDeleteVoiceModel = settingsViewModel::deleteVoiceModel,
                        onVoiceResponseModeChange = settingsViewModel::setVoiceResponseMode,
                        onCommandRecordingEnabledChange = settingsViewModel::setCommandRecordingEnabled,
                        onCommandRecordingRetentionChange = settingsViewModel::setCommandRecordingRetention,
                        onClearCommandRecordings = settingsViewModel::clearCommandRecordings,
                        onVadSilenceMsChange = settingsViewModel::setVadSilenceMs,
                    ),
                doorbell =
                    DoorbellSettingsActions(
                        onDoorbellEnabledChange = settingsViewModel::setDoorbellEnabled,
                        onDoorbellAutoCloseSecChange = settingsViewModel::setDoorbellAutoCloseSec,
                        onUpsertDoorbell = settingsViewModel::upsertDoorbell,
                        onRemoveDoorbell = settingsViewModel::removeDoorbell,
                        onTestDoorbell = onTestDoorbell,
                    ),
                camera =
                    CameraSettingsActions(
                        onRequestCameraEnable = onRequestCameraEnable,
                        onCameraDisable = { settingsViewModel.setCameraEnabled(false) },
                        onFacingChange = settingsViewModel::setCameraFacing,
                        onResolutionChange = settingsViewModel::setCameraResolution,
                        onMotionModeChange = settingsViewModel::setCameraMotionMode,
                        onMotionSensitivityChange = settingsViewModel::setCameraMotionSensitivity,
                        onMotionClearDelayChange = settingsViewModel::setCameraMotionClearDelay,
                        onWakeOnMotionChange = settingsViewModel::setCameraWakeOnMotion,
                    ),
                esphome =
                    EsphomeSettingsActions(
                        onEsphomeEnabledChange = settingsViewModel::setEsphomeEnabled,
                        onSavePskBase64 = settingsViewModel::setNoisePskBase64,
                        onClearPsk = settingsViewModel::clearNoisePsk,
                    ),
                screensaver =
                    ScreensaverSettingsActions(
                        onDayScreensaverModeChange = settingsViewModel::setDayScreensaverMode,
                        onNightScreensaverModeChange = settingsViewModel::setNightScreensaverMode,
                        onIdleTimeoutSecChange = settingsViewModel::setIdleTimeoutSec,
                        onWeatherEntityIdChange = settingsViewModel::setWeatherEntityId,
                        onCalendarEntityIdChange = settingsViewModel::setCalendarEntityId,
                        onPowerUsageEntityIdChange = settingsViewModel::setPowerUsageEntityId,
                        onSolarPowerEntityIdChange = settingsViewModel::setSolarPowerEntityId,
                        onGridPowerEntityIdChange = settingsViewModel::setGridPowerEntityId,
                        onOverlayPositionChange = settingsViewModel::setOverlayPosition,
                        onPictureSpacingDpChange = settingsViewModel::setPictureSpacingDp,
                        onMediaLibrarySourceChange = settingsViewModel::setMediaLibrarySource,
                        onMediaLibraryOrderChange = settingsViewModel::setMediaLibraryOrder,
                        onTestScreensaver = onTestScreensaver,
                    ),
                immich =
                    ImmichSettingsActions(
                        onImmichUrlChange = settingsViewModel::setImmichUrl,
                        onImmichApiKeyChange = settingsViewModel::setImmichApiKey,
                        onImmichAlbumChange = settingsViewModel::setImmichAlbum,
                        onImmichCatalogTtlHoursChange = settingsViewModel::setImmichCatalogTtlHours,
                        onRefreshImmichCatalog = settingsViewModel::refreshImmichCatalog,
                        onTestImmich = onTestImmich,
                    ),
                sidebar =
                    SidebarSettingsActions(
                        onPositionChange = settingsViewModel::setSidebarPosition,
                        onPinnedChange = settingsViewModel::setSidebarPinned,
                        onShowLabelsChange = settingsViewModel::setSidebarShowLabels,
                        onEdgeHandleChange = settingsViewModel::setSidebarEdgeHandle,
                        onShortcutsChange = settingsViewModel::setSidebarShortcuts,
                    ),
                admin =
                    AdminSettingsActions(
                        onBatteryHelp = onBatteryHelp,
                        onOpenWsDebug = onOpenWsDebug,
                    ),
                onBack = onBack,
            )
        }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsContent(
            state = state,
            actions = actions,
            mediaSourcePicker = mediaSourcePicker,
        )
        KioskOverlays(
            state =
                KioskOverlayState(
                    doorbellState = state.doorbellState,
                    doorbellAutoCloseSec = state.doorbellAutoCloseSec,
                    haBaseUrl = state.haUrl ?: "",
                    isIdle = state.isIdle,
                ),
            bearerTokenProvider = bearerTokenProvider,
            fetchHlsUrl = fetchHlsUrl,
            onCloseDoorbell = onCloseDoorbell,
            onTapScreensaver = onTapScreensaver,
            screensaverContent = screensaverContent,
        )
    }
}

private suspend fun runImmichTest(
    context: Context,
    httpClient: HttpClient,
    url: String,
    apiKey: String,
    album: String,
): String {
    val client = ImmichApiClient(httpClient, url, apiKey)
    return when (val probe = client.probe()) {
        is ImmichProbeResult.Unreachable ->
            context.getString(R.string.settings_immich_test_unreachable, probe.reason)
        is ImmichProbeResult.MissingScope ->
            context.getString(
                R.string.settings_immich_test_missing_scope,
                probe.scope,
                probe.statusCode,
            )
        ImmichProbeResult.Authenticated -> {
            val albums = client.listAlbums()
            val count = albums.size
            val match =
                if (album.isBlank()) {
                    null
                } else {
                    albums.firstOrNull { it.albumName.equals(album, ignoreCase = true) }
                }
            val albumStatus =
                when {
                    album.isBlank() -> context.getString(R.string.settings_immich_test_no_album_filter)
                    match != null -> context.getString(R.string.settings_immich_test_album_found, album)
                    else ->
                        context.resources.getQuantityString(
                            R.plurals.settings_immich_test_album_not_found,
                            count,
                            album,
                            count,
                        )
                }
            val albumIdToProbe = match?.id ?: albums.firstOrNull()?.id
            val assetViewOk =
                if (albumIdToProbe != null) {
                    client.canViewAsset(albumIdToProbe)
                } else {
                    null
                }
            val assetStatus =
                when (assetViewOk) {
                    true -> context.getString(R.string.settings_immich_test_thumbnails_ok)
                    false -> context.getString(R.string.settings_immich_test_missing_asset_view)
                    null -> context.getString(R.string.settings_immich_test_no_assets)
                }
            context.resources.getQuantityString(
                R.plurals.settings_immich_test_connected,
                count,
                count,
                albumStatus,
                assetStatus,
            )
        }
    }
}

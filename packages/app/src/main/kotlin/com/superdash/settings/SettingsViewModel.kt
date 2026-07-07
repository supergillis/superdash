package com.superdash.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.superdash.AppGraph
import com.superdash.R
import com.superdash.core.locale.SupportedLanguage
import com.superdash.core.resources.StringProvider
import com.superdash.core.util.UrlNormalizer
import com.superdash.doorbell.DoorbellConfig
import com.superdash.doorbell.DoorbellSettings
import com.superdash.doorbell.DoorbellState
import com.superdash.ha.EntityState
import com.superdash.ha.HaConnectionState
import com.superdash.kiosk.KioskSettings
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarSettings
import com.superdash.kiosk.SidebarShortcut
import com.superdash.screensaver.ScreensaverMode
import com.superdash.screensaver.ScreensaverSettings
import com.superdash.screensaver.overlay.OverlayPosition
import com.superdash.voice.VoiceSettings
import com.superdash.voice.models.VoiceModelRow
import com.superdash.voice.models.VoiceModelState
import com.superdash.voice.pipeline.VoiceResponseMode
import com.superdash.voice.pipeline.VoiceSttProvider
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RefreshImmichCatalogResult {
    data class Success(
        val itemCount: Int,
    ) : RefreshImmichCatalogResult

    data class Failure(
        val reason: String,
    ) : RefreshImmichCatalogResult
}

/**
 * Actions the ViewModel delegates to outside of the XSettings setters.
 *
 * Covers voice-model download/delete (owned by `VoiceModelRepository`),
 * command recording clear (owned by `VoiceRecordingComponent`), and the
 * residual Immich/HA writes that still live on [SettingsRepository].
 */
interface SettingsExternalActions {
    suspend fun setHaUrl(value: String)

    suspend fun setImmichUrl(value: String)

    suspend fun setImmichApiKey(value: String)

    suspend fun setImmichAlbum(value: String)

    suspend fun setImmichCatalogTtlHours(value: Int)

    suspend fun refreshImmichCatalog(): RefreshImmichCatalogResult

    suspend fun downloadVoiceModel(modelId: String)

    suspend fun deleteVoiceModel(modelId: String)

    suspend fun clearCommandRecordings()
}

class SettingsViewModel(
    private val kioskSettings: KioskSettings,
    private val sidebarSettings: SidebarSettings,
    private val voiceSettings: VoiceSettings,
    private val doorbellSettings: DoorbellSettings,
    private val screensaverSettings: ScreensaverSettings,
    private val esphomePskStore: PskStore,
    private val localeController: LocaleSettingsController,
    haUrlFlow: Flow<String?>,
    immichUrlFlow: Flow<String>,
    immichApiKeyFlow: Flow<String>,
    immichAlbumFlow: Flow<String>,
    immichCatalogTtlHoursFlow: Flow<Int> = MutableStateFlow(24),
    voiceModelStateFlow: Flow<VoiceModelState>,
    haStateFlow: Flow<HaConnectionState>,
    entitiesFlow: Flow<Map<String, EntityState>>,
    doorbellStateFlow: Flow<DoorbellState>,
    isIdleFlow: Flow<Boolean>,
    private val actions: SettingsExternalActions,
    private val strings: StringProvider,
) : ViewModel() {
    // AndroidX ViewModels survive the Activity recreate that a per-app locale switch
    // triggers, so the current language must be tracked reactively rather than cached
    // once at construction time.
    private val languageFlow = MutableStateFlow(localeController.currentLanguage())

    private val voiceUiStateFlow: Flow<VoiceSettingsState> =
        combine(
            combine(
                voiceSettings.enabled,
                voiceSettings.activeWakeWord,
                voiceSettings.assistProvider,
                voiceSettings.primarySttProvider,
                voiceSettings.secondarySttProvider,
            ) { enabled, wakeWord, assist, primary, secondary ->
                VoiceCore(enabled, wakeWord, assist, primary, secondary)
            },
            combine(
                voiceSettings.selectedSttModelId,
                voiceSettings.selectedIntentEmbeddingModelId,
                voiceSettings.localIntentRecognizerEnabled,
                voiceModelStateFlow,
            ) { sttModelId, intentModelId, localIntent, modelState ->
                VoiceModels(sttModelId, intentModelId, localIntent, modelState.models)
            },
            combine(
                voiceSettings.responseMode,
                voiceSettings.commandRecordingEnabled,
                voiceSettings.commandRecordingRetention,
                voiceSettings.vadSilenceMs,
            ) { responseMode, recordingEnabled, retention, vadMs ->
                VoiceTail(responseMode, recordingEnabled, retention, vadMs)
            },
        ) { core, models, tail ->
            VoiceSettingsState(
                voiceEnabled = core.enabled,
                activeWakeWord = core.wakeWord,
                assistProvider = VoiceSttProvider.fromKey(core.assistProviderKey),
                primarySttProvider = VoiceSttProvider.fromKey(core.primarySttProviderKey),
                secondarySttProvider = VoiceSttProvider.fromKey(core.secondarySttProviderKey),
                selectedSttModelId = models.selectedSttModelId,
                selectedIntentEmbeddingModelId = models.selectedIntentEmbeddingModelId,
                localIntentRecognizerEnabled = models.localIntentRecognizerEnabled,
                voiceModels = models.voiceModels,
                responseMode = VoiceResponseMode.fromKey(tail.responseModeKey),
                commandRecordingEnabled = tail.commandRecordingEnabled,
                commandRecordingRetention = tail.commandRecordingRetention,
                vadSilenceMs = tail.vadSilenceMs,
            )
        }

    private val screensaverUiStateFlow: Flow<ScreensaverSettingsState> =
        combine(
            combine(screensaverSettings.dayMode, screensaverSettings.nightMode) { day, night ->
                ScreensaverMode.fromKey(day) to ScreensaverMode.fromKey(night)
            },
            combine(
                screensaverSettings.idleTimeoutSec,
                screensaverSettings.weatherEntityId,
                screensaverSettings.calendarEntityId,
                screensaverSettings.overlayPosition,
                screensaverSettings.pictureSpacingDp,
            ) { timeout, weather, calendar, overlay, spacing ->
                ScreensaverDisplay(
                    timeoutSec = timeout,
                    weatherEntityId = weather,
                    calendarEntityId = calendar,
                    overlayPosition = OverlayPosition.fromKey(overlay),
                    pictureSpacingDp = spacing,
                )
            },
            combine(
                screensaverSettings.mediaLibrarySourceId,
                screensaverSettings.mediaLibrarySourceTitle,
                screensaverSettings.mediaLibraryOrder,
            ) { id, title, order ->
                Triple(id, title, order)
            },
            combine(
                screensaverSettings.powerUsageEntityId,
                screensaverSettings.solarPowerEntityId,
                screensaverSettings.gridPowerEntityId,
            ) { usage, solar, grid ->
                ScreensaverEnergy(usage, solar, grid)
            },
        ) { modes, display, media, energy ->
            ScreensaverSettingsState(
                dayMode = modes.first,
                nightMode = modes.second,
                idleTimeoutSec = display.timeoutSec,
                weatherEntityId = display.weatherEntityId,
                calendarEntityId = display.calendarEntityId,
                powerUsageEntityId = energy.powerUsageEntityId,
                solarPowerEntityId = energy.solarPowerEntityId,
                gridPowerEntityId = energy.gridPowerEntityId,
                overlayPosition = display.overlayPosition,
                pictureSpacingDp = display.pictureSpacingDp,
                mediaLibrarySourceId = media.first,
                mediaLibrarySourceTitle = media.second,
                mediaLibraryOrderKey = media.third,
            )
        }

    private val doorbellUiStateFlow: Flow<DoorbellSettingsState> =
        combine(
            doorbellSettings.enabled,
            doorbellSettings.doorbells,
            doorbellSettings.autoCloseSec,
        ) { enabled, configs, autoClose ->
            DoorbellSettingsState(
                enabled = enabled,
                configs = configs.toImmutableList(),
                autoCloseSec = autoClose,
            )
        }

    private val connectionUiStateFlow: Flow<ConnectionEntities> =
        combine(
            haUrlFlow,
            kioskSettings.dashboardPath,
            haStateFlow,
            entitiesFlow,
        ) { url, path, state, entities ->
            ConnectionEntities(
                connection =
                    ConnectionSettingsState(
                        haUrl = url,
                        dashboardPath = path,
                        haState = state,
                        entityCount = entities.size,
                    ),
                entities = entities.values.sortedBy { it.entityId }.toImmutableList(),
            )
        }

    private val deviceUiStateFlow: Flow<DeviceSettingsState> =
        combine(kioskSettings.keepScreenOn, kioskSettings.startOnBoot) { keep, boot ->
            DeviceSettingsState(keepScreenOn = keep, startOnBoot = boot)
        }

    private val immichUiStateFlow: Flow<ImmichSettingsState> =
        combine(
            immichUrlFlow,
            immichApiKeyFlow,
            immichAlbumFlow,
            immichCatalogTtlHoursFlow,
        ) { url, apiKey, album, ttl ->
            ImmichSettingsState(url = url, apiKey = apiKey, album = album, catalogTtlHours = ttl)
        }

    private val overlayUiStateFlow: Flow<SettingsOverlayState> =
        combine(doorbellStateFlow, isIdleFlow) { doorbell, idle ->
            SettingsOverlayState(doorbellState = doorbell, isIdle = idle)
        }

    private val esphomeUiStateFlow: Flow<EsphomeSettingsState> =
        combine(kioskSettings.esphomeEnabled, esphomePskStore.psk) { enabled, psk ->
            EsphomeSettingsState(
                enabled = enabled,
                pskState =
                    if (psk == null) {
                        PskState.NotSet
                    } else {
                        PskState.Configured(fingerprint = fingerprintOf(psk))
                    },
            )
        }

    private val sidebarUiStateFlow: Flow<SidebarSettingsState> =
        combine(
            sidebarSettings.position,
            sidebarSettings.pinned,
            sidebarSettings.showLabels,
            sidebarSettings.shortcuts,
        ) { position, pinned, showLabels, shortcuts ->
            SidebarSettingsState(
                position = position,
                pinned = pinned,
                showLabels = showLabels,
                shortcuts = shortcuts.toImmutableList(),
            )
        }

    private fun fingerprintOf(psk: ByteArray): String {
        val b64 = android.util.Base64.encodeToString(psk, android.util.Base64.NO_WRAP)
        return if (b64.length <= 9) b64 else "${b64.take(4)}…${b64.takeLast(4)}"
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            combine(connectionUiStateFlow, deviceUiStateFlow, esphomeUiStateFlow, overlayUiStateFlow) {
                conn,
                device,
                esphome,
                overlay,
                ->
                CoreUi(conn, device, esphome, overlay)
            },
            combine(
                voiceUiStateFlow,
                doorbellUiStateFlow,
                screensaverUiStateFlow,
                immichUiStateFlow,
                sidebarUiStateFlow,
            ) {
                voice,
                doorbell,
                screensaver,
                immich,
                sidebar,
                ->
                FeatureUi(voice, doorbell, screensaver, immich, sidebar)
            },
            languageFlow,
        ) { core, features, language ->
            SettingsUiState(
                connection = core.conn.connection,
                haEntities = core.conn.entities,
                device = core.device,
                voice = features.voice,
                doorbell = features.doorbell,
                esphome = core.esphome,
                screensaver = features.screensaver,
                immich = features.immich,
                sidebar = features.sidebar,
                overlay = core.overlay,
                general =
                    GeneralSettingsState(
                        currentLanguage = language,
                        languagePickerAvailable = localeController.isPerAppLanguageSupported(),
                    ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.empty())

    private data class CoreUi(
        val conn: ConnectionEntities,
        val device: DeviceSettingsState,
        val esphome: EsphomeSettingsState,
        val overlay: SettingsOverlayState,
    )

    private data class FeatureUi(
        val voice: VoiceSettingsState,
        val doorbell: DoorbellSettingsState,
        val screensaver: ScreensaverSettingsState,
        val immich: ImmichSettingsState,
        val sidebar: SidebarSettingsState,
    )

    private data class ConnectionEntities(
        val connection: ConnectionSettingsState,
        val entities: kotlinx.collections.immutable.ImmutableList<EntityState>,
    )

    private data class VoiceCore(
        val enabled: Boolean,
        val wakeWord: String,
        val assistProviderKey: String,
        val primarySttProviderKey: String,
        val secondarySttProviderKey: String,
    )

    private data class VoiceModels(
        val selectedSttModelId: String,
        val selectedIntentEmbeddingModelId: String,
        val localIntentRecognizerEnabled: Boolean,
        val voiceModels: List<VoiceModelRow>,
    )

    private data class VoiceTail(
        val responseModeKey: String,
        val commandRecordingEnabled: Boolean,
        val commandRecordingRetention: Int,
        val vadSilenceMs: Int,
    )

    private data class ScreensaverDisplay(
        val timeoutSec: Int,
        val weatherEntityId: String,
        val calendarEntityId: String,
        val overlayPosition: OverlayPosition,
        val pictureSpacingDp: Int,
    )

    private data class ScreensaverEnergy(
        val powerUsageEntityId: String,
        val solarPowerEntityId: String,
        val gridPowerEntityId: String,
    )

    fun setLanguage(value: SupportedLanguage?) {
        localeController.setLanguage(value)
        languageFlow.value = value
    }

    fun setHaUrl(value: String) =
        launch { actions.setHaUrl(UrlNormalizer.normalize(value) ?: "") }

    fun setDashboardPath(value: String) = launch { kioskSettings.setDashboardPath(value) }

    fun setKeepScreenOn(value: Boolean) = launch { kioskSettings.setKeepScreenOn(value) }

    fun setStartOnBoot(value: Boolean) = launch { kioskSettings.setStartOnBoot(value) }

    fun setVoiceEnabled(value: Boolean) = launch { voiceSettings.setEnabled(value) }

    fun setActiveWakeWord(value: String) = launch { voiceSettings.setActiveWakeWord(value) }

    fun setVoiceAssistProvider(value: VoiceSttProvider) = launch { voiceSettings.setAssistProvider(value.key) }

    fun setPrimarySttProvider(value: VoiceSttProvider) = launch { voiceSettings.setPrimarySttProvider(value.key) }

    fun setSecondarySttProvider(value: VoiceSttProvider) = launch { voiceSettings.setSecondarySttProvider(value.key) }

    fun setSelectedSttModelId(value: String) = launch { voiceSettings.setSelectedSttModelId(value) }

    fun setSelectedIntentEmbeddingModelId(value: String) =
        launch { voiceSettings.setSelectedIntentEmbeddingModelId(value) }

    fun setLocalIntentRecognizerEnabled(value: Boolean) =
        launch { voiceSettings.setLocalIntentRecognizerEnabled(value) }

    fun downloadVoiceModel(modelId: String) = launch { actions.downloadVoiceModel(modelId) }

    fun deleteVoiceModel(modelId: String) = launch { actions.deleteVoiceModel(modelId) }

    fun setVoiceResponseMode(value: VoiceResponseMode) = launch { voiceSettings.setResponseMode(value.key) }

    fun setCommandRecordingEnabled(value: Boolean) = launch { voiceSettings.setCommandRecordingEnabled(value) }

    fun setCommandRecordingRetention(value: Int) = launch { voiceSettings.setCommandRecordingRetention(value) }

    fun clearCommandRecordings() = launch { actions.clearCommandRecordings() }

    fun setVadSilenceMs(value: Int) = launch { voiceSettings.setVadSilenceMs(value) }

    fun setDayScreensaverMode(value: String) = launch { screensaverSettings.setDayMode(value) }

    fun setNightScreensaverMode(value: String) = launch { screensaverSettings.setNightMode(value) }

    fun setIdleTimeoutSec(value: Int) = launch { screensaverSettings.setIdleTimeoutSec(value) }

    fun setWeatherEntityId(value: String) = launch { screensaverSettings.setWeatherEntityId(value) }

    fun setCalendarEntityId(value: String) = launch { screensaverSettings.setCalendarEntityId(value) }

    fun setPowerUsageEntityId(value: String) = launch { screensaverSettings.setPowerUsageEntityId(value) }

    fun setSolarPowerEntityId(value: String) = launch { screensaverSettings.setSolarPowerEntityId(value) }

    fun setGridPowerEntityId(value: String) = launch { screensaverSettings.setGridPowerEntityId(value) }

    fun setOverlayPosition(value: String) = launch { screensaverSettings.setOverlayPosition(value) }

    fun setPictureSpacingDp(value: Int) = launch { screensaverSettings.setPictureSpacingDp(value) }

    fun setMediaLibrarySource(
        id: String?,
        title: String?,
    ) = launch { screensaverSettings.setMediaLibrarySource(id, title) }

    fun setMediaLibraryOrder(value: String) = launch { screensaverSettings.setMediaLibraryOrder(value) }

    fun setImmichUrl(value: String) = launch { actions.setImmichUrl(value) }

    fun setImmichApiKey(value: String) = launch { actions.setImmichApiKey(value) }

    fun setImmichAlbum(value: String) = launch { actions.setImmichAlbum(value) }

    fun setImmichCatalogTtlHours(value: Int) = launch { actions.setImmichCatalogTtlHours(value) }

    suspend fun refreshImmichCatalog(): String =
        when (val result = actions.refreshImmichCatalog()) {
            is RefreshImmichCatalogResult.Success ->
                strings.getQuantity(
                    R.plurals.settings_immich_refresh_success,
                    result.itemCount,
                    result.itemCount,
                )
            is RefreshImmichCatalogResult.Failure ->
                strings.get(R.string.settings_immich_refresh_failed, result.reason)
        }

    fun setDoorbellEnabled(value: Boolean) = launch { doorbellSettings.setEnabled(value) }

    fun setDoorbellAutoCloseSec(value: Int) = launch { doorbellSettings.setAutoCloseSec(value) }

    fun upsertDoorbell(config: DoorbellConfig) = launch { doorbellSettings.upsertDoorbell(config) }

    fun removeDoorbell(id: String) = launch { doorbellSettings.removeDoorbell(id) }

    fun setEsphomeEnabled(value: Boolean) = launch { kioskSettings.setEsphomeEnabled(value) }

    fun setSidebarPosition(value: SidebarPosition) = launch { sidebarSettings.setPosition(value) }

    fun setSidebarPinned(value: Boolean) = launch { sidebarSettings.setPinned(value) }

    fun setSidebarShowLabels(value: Boolean) = launch { sidebarSettings.setShowLabels(value) }

    fun setSidebarShortcuts(value: List<SidebarShortcut>) = launch { sidebarSettings.setShortcuts(value) }

    fun setNoisePskBase64(value: String): Boolean {
        val raw =
            try {
                android.util.Base64.decode(value.trim(), android.util.Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                return false
            }
        if (raw.size != 32) {
            return false
        }
        viewModelScope.launch { esphomePskStore.set(raw) }
        return true
    }

    fun clearNoisePsk() {
        viewModelScope.launch { esphomePskStore.clear() }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(
        private val graph: AppGraph,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(
                kioskSettings = graph.kioskSettings,
                sidebarSettings = graph.sidebarSettings,
                voiceSettings = graph.voiceSettings,
                doorbellSettings = graph.doorbellSettings,
                screensaverSettings = graph.screensaverSettings,
                esphomePskStore =
                    object : PskStore {
                        override val psk: Flow<ByteArray?> = graph.esphomePsk.psk

                        override suspend fun set(psk: ByteArray) = graph.esphomePsk.set(psk)

                        override suspend fun clear() = graph.esphomePsk.clear()
                    },
                haUrlFlow = graph.settings.haUrl,
                immichUrlFlow = graph.immichSettings.url,
                immichApiKeyFlow = graph.immichSettings.apiKey,
                immichAlbumFlow = graph.immichSettings.album,
                immichCatalogTtlHoursFlow = graph.immichSettings.catalogTtlHours,
                voiceModelStateFlow = graph.voiceModels.state,
                haStateFlow = graph.haClient.state,
                entitiesFlow = graph.haClient.entities,
                doorbellStateFlow = graph.doorbellOverlayController.state,
                isIdleFlow = graph.idleController.isIdle,
                strings = graph.strings,
                actions =
                    object : SettingsExternalActions {
                        override suspend fun setHaUrl(value: String) {
                            graph.settings.setHaUrl(value)
                        }

                        override suspend fun setImmichUrl(value: String) {
                            graph.immichSettings.setUrl(value)
                        }

                        override suspend fun setImmichApiKey(value: String) {
                            graph.immichSettings.setApiKey(value)
                        }

                        override suspend fun setImmichAlbum(value: String) {
                            graph.immichSettings.setAlbum(value)
                        }

                        override suspend fun setImmichCatalogTtlHours(value: Int) {
                            graph.immichSettings.setCatalogTtlHours(value)
                        }

                        override suspend fun refreshImmichCatalog(): RefreshImmichCatalogResult =
                            graph.immich.refreshCatalogNow()

                        override suspend fun downloadVoiceModel(modelId: String) {
                            graph.voiceModels.downloadAndInstall(modelId)
                        }

                        override suspend fun deleteVoiceModel(modelId: String) {
                            withContext(Dispatchers.IO) {
                                graph.voiceModels.delete(modelId)
                            }
                        }

                        override suspend fun clearCommandRecordings() {
                            graph.voice.recordingComponent.clear()
                        }
                    },
                localeController =
                    object : LocaleSettingsController {
                        override fun isPerAppLanguageSupported(): Boolean =
                            graph.localeController.isPerAppLanguageSupported()

                        override fun currentLanguage(): SupportedLanguage? =
                            graph.localeController.currentLanguage()

                        override fun setLanguage(language: SupportedLanguage?) {
                            graph.localeController.setLanguage(language)
                        }
                    },
            ) as T
    }
}

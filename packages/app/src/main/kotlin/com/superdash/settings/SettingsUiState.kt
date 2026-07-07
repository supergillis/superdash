package com.superdash.settings

import androidx.compose.runtime.Immutable
import com.superdash.core.locale.SupportedLanguage
import com.superdash.doorbell.DoorbellConfig
import com.superdash.doorbell.DoorbellState
import com.superdash.ha.EntityState
import com.superdash.ha.HaConnectionState
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarSettingsDefaults
import com.superdash.kiosk.SidebarShortcut
import com.superdash.screensaver.ScreensaverMode
import com.superdash.screensaver.overlay.OverlayPosition
import com.superdash.voice.models.VoiceModelIds
import com.superdash.voice.models.VoiceModelRow
import com.superdash.voice.pipeline.VoiceResponseMode
import com.superdash.voice.pipeline.VoiceSttProvider
import com.superdash.voice.wake.WakeWordModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class SettingsUiState(
    val connection: ConnectionSettingsState,
    val haEntities: ImmutableList<EntityState>,
    val device: DeviceSettingsState,
    val voice: VoiceSettingsState,
    val doorbell: DoorbellSettingsState,
    val esphome: EsphomeSettingsState,
    val screensaver: ScreensaverSettingsState,
    val immich: ImmichSettingsState,
    val sidebar: SidebarSettingsState,
    val overlay: SettingsOverlayState,
    val general: GeneralSettingsState = GeneralSettingsState(),
) {
    val haUrl: String? get() = connection.haUrl
    val dashboardPath: String get() = connection.dashboardPath
    val haState: HaConnectionState get() = connection.haState
    val entityCount: Int get() = connection.entityCount
    val keepScreenOn: Boolean get() = device.keepScreenOn
    val startOnBoot: Boolean get() = device.startOnBoot
    val voiceEnabled: Boolean get() = voice.voiceEnabled
    val activeWakeWord: String get() = voice.activeWakeWord
    val vadSilenceMs: Int get() = voice.vadSilenceMs
    val doorbellEnabled: Boolean get() = doorbell.enabled
    val doorbells: ImmutableList<DoorbellConfig> get() = doorbell.configs
    val doorbellAutoCloseSec: Int get() = doorbell.autoCloseSec
    val esphomeEnabled: Boolean get() = esphome.enabled
    val dayScreensaverMode: ScreensaverMode get() = screensaver.dayMode
    val nightScreensaverMode: ScreensaverMode get() = screensaver.nightMode
    val idleTimeoutSec: Int get() = screensaver.idleTimeoutSec
    val weatherEntityId: String get() = screensaver.weatherEntityId
    val calendarEntityId: String get() = screensaver.calendarEntityId
    val overlayPosition: OverlayPosition get() = screensaver.overlayPosition
    val pictureSpacingDp: Int get() = screensaver.pictureSpacingDp
    val mediaLibrarySourceId: String? get() = screensaver.mediaLibrarySourceId
    val mediaLibrarySourceTitle: String? get() = screensaver.mediaLibrarySourceTitle
    val mediaLibraryOrderKey: String get() = screensaver.mediaLibraryOrderKey
    val immichUrl: String get() = immich.url
    val immichApiKey: String get() = immich.apiKey
    val immichAlbum: String get() = immich.album
    val doorbellState: DoorbellState get() = overlay.doorbellState
    val isIdle: Boolean get() = overlay.isIdle

    companion object {
        fun empty(): SettingsUiState =
            SettingsUiState(
                connection = ConnectionSettingsState.empty(),
                haEntities = persistentListOf(),
                device = DeviceSettingsState.empty(),
                voice = VoiceSettingsState.empty(),
                doorbell = DoorbellSettingsState.empty(),
                esphome = EsphomeSettingsState.empty(),
                screensaver = ScreensaverSettingsState.empty(),
                immich = ImmichSettingsState.empty(),
                sidebar = SidebarSettingsState.empty(),
                overlay = SettingsOverlayState.empty(),
                general = GeneralSettingsState(),
            )
    }
}

@Immutable
data class GeneralSettingsState(
    val currentLanguage: SupportedLanguage? = null,
)

@Immutable
data class ConnectionSettingsState(
    val haUrl: String?,
    val dashboardPath: String,
    val haState: HaConnectionState,
    val entityCount: Int,
) {
    companion object {
        fun empty(): ConnectionSettingsState =
            ConnectionSettingsState(
                haUrl = null,
                dashboardPath = "",
                haState = HaConnectionState.Disconnected,
                entityCount = 0,
            )
    }
}

@Immutable
data class DeviceSettingsState(
    val keepScreenOn: Boolean,
    val startOnBoot: Boolean,
) {
    companion object {
        fun empty(): DeviceSettingsState =
            DeviceSettingsState(
                keepScreenOn = true,
                startOnBoot = true,
            )
    }
}

@Immutable
data class VoiceSettingsState(
    val voiceEnabled: Boolean,
    val activeWakeWord: String,
    val assistProvider: VoiceSttProvider,
    val primarySttProvider: VoiceSttProvider,
    val secondarySttProvider: VoiceSttProvider,
    val selectedSttModelId: String,
    val selectedIntentEmbeddingModelId: String,
    val localIntentRecognizerEnabled: Boolean,
    val voiceModels: List<VoiceModelRow>,
    val responseMode: VoiceResponseMode,
    val commandRecordingEnabled: Boolean,
    val commandRecordingRetention: Int,
    val vadSilenceMs: Int,
) {
    companion object {
        fun empty(): VoiceSettingsState =
            VoiceSettingsState(
                voiceEnabled = false,
                activeWakeWord = WakeWordModel.DEFAULT_ID,
                assistProvider = VoiceSttProvider.HaAssist,
                primarySttProvider = VoiceSttProvider.HaAssist,
                secondarySttProvider = VoiceSttProvider.None,
                selectedSttModelId = VoiceModelIds.DEFAULT_STT_MODEL_ID,
                selectedIntentEmbeddingModelId = VoiceModelIds.DEFAULT_INTENT_EMBEDDING_MODEL_ID,
                localIntentRecognizerEnabled = false,
                voiceModels = emptyList(),
                responseMode = VoiceResponseMode.Speak,
                commandRecordingEnabled = false,
                commandRecordingRetention = 100,
                vadSilenceMs = 500,
            )
    }
}

@Immutable
data class DoorbellSettingsState(
    val enabled: Boolean,
    val configs: ImmutableList<DoorbellConfig>,
    val autoCloseSec: Int,
) {
    companion object {
        fun empty(): DoorbellSettingsState =
            DoorbellSettingsState(
                enabled = false,
                configs = persistentListOf(),
                autoCloseSec = 60,
            )
    }
}

@Immutable
data class EsphomeSettingsState(
    val enabled: Boolean,
    val pskState: PskState,
) {
    companion object {
        fun empty(): EsphomeSettingsState =
            EsphomeSettingsState(enabled = false, pskState = PskState.NotSet)
    }
}

@Immutable
sealed interface PskState {
    data object NotSet : PskState

    data class Configured(
        val fingerprint: String,
    ) : PskState
}

@Immutable
data class ScreensaverSettingsState(
    val dayMode: ScreensaverMode,
    val nightMode: ScreensaverMode,
    val idleTimeoutSec: Int,
    val weatherEntityId: String,
    val calendarEntityId: String,
    val powerUsageEntityId: String,
    val solarPowerEntityId: String,
    val gridPowerEntityId: String,
    val overlayPosition: OverlayPosition,
    val pictureSpacingDp: Int,
    val mediaLibrarySourceId: String?,
    val mediaLibrarySourceTitle: String?,
    val mediaLibraryOrderKey: String,
) {
    companion object {
        fun empty(): ScreensaverSettingsState =
            ScreensaverSettingsState(
                dayMode = ScreensaverMode.Photos,
                nightMode = ScreensaverMode.Black,
                idleTimeoutSec = 300,
                weatherEntityId = "weather.home",
                calendarEntityId = "",
                powerUsageEntityId = "",
                solarPowerEntityId = "",
                gridPowerEntityId = "",
                overlayPosition = OverlayPosition.BottomLeft,
                pictureSpacingDp = 8,
                mediaLibrarySourceId = null,
                mediaLibrarySourceTitle = null,
                mediaLibraryOrderKey = "shuffle",
            )
    }
}

@Immutable
data class ImmichSettingsState(
    val url: String,
    val apiKey: String,
    val album: String,
    val catalogTtlHours: Int,
) {
    companion object {
        fun empty(): ImmichSettingsState =
            ImmichSettingsState(
                url = "",
                apiKey = "",
                album = "",
                catalogTtlHours = 24,
            )
    }
}

@Immutable
data class SidebarSettingsState(
    val position: SidebarPosition,
    val pinned: Boolean,
    val showLabels: Boolean,
    val shortcuts: ImmutableList<SidebarShortcut>,
) {
    companion object {
        fun empty(): SidebarSettingsState =
            SidebarSettingsState(
                position = SidebarSettingsDefaults.position,
                pinned = SidebarSettingsDefaults.pinned,
                showLabels = SidebarSettingsDefaults.showLabels,
                shortcuts = SidebarSettingsDefaults.shortcuts.toImmutableList(),
            )
    }
}

@Immutable
data class SettingsOverlayState(
    val doorbellState: DoorbellState,
    val isIdle: Boolean,
) {
    companion object {
        fun empty(): SettingsOverlayState =
            SettingsOverlayState(
                doorbellState = DoorbellState.Idle,
                isIdle = false,
            )
    }
}

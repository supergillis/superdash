package com.superdash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.superdash.doorbell.DoorbellState
import com.superdash.ha.HaConnectionState
import com.superdash.ha.HaTokens
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarShortcut
import com.superdash.kiosk.ui.AppState
import com.superdash.screensaver.ScreensaverMode
import com.superdash.voice.VoiceServiceRunPolicy
import com.superdash.voice.pipeline.VoiceState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    haUrlFlow: Flow<String?>,
    dashboardPathFlow: Flow<String>,
    tokensFlow: Flow<HaTokens?>,
    haStateFlow: Flow<HaConnectionState>,
    voiceStateFlow: Flow<VoiceState>,
    isIdleFlow: Flow<Boolean>,
    dayScreensaverModeKeyFlow: Flow<String>,
    nightScreensaverModeKeyFlow: Flow<String>,
    nightModeActiveFlow: Flow<Boolean>,
    doorbellStateFlow: Flow<DoorbellState>,
    doorbellAutoCloseSecFlow: Flow<Int>,
    voiceEnabledFlow: Flow<Boolean>,
    sidebarPositionFlow: Flow<SidebarPosition>,
    sidebarPinnedFlow: Flow<Boolean>,
    sidebarShowLabelsFlow: Flow<Boolean>,
    sidebarShortcutsFlow: Flow<List<SidebarShortcut>>,
    sidebarEdgeHandleFlow: Flow<Boolean>,
) : ViewModel() {
    private val connectionFlow =
        combine(haUrlFlow, dashboardPathFlow, tokensFlow, haStateFlow) { haUrl, dashboardPath, tokens, haState ->
            MainConnectionState(
                haUrl = haUrl,
                dashboardPath = dashboardPath,
                tokens = tokens,
                haState = haState,
            )
        }

    private val environmentFlow =
        combine(
            voiceStateFlow,
            isIdleFlow,
            dayScreensaverModeKeyFlow,
            nightScreensaverModeKeyFlow,
            nightModeActiveFlow,
        ) { voiceState, isIdle, dayKey, nightKey, nightActive ->
            MainEnvironmentState(
                voiceState = voiceState,
                isIdle = isIdle,
                dayKey = dayKey,
                nightKey = nightKey,
                nightActive = nightActive,
            )
        }

    private val doorbellFlow =
        combine(doorbellStateFlow, doorbellAutoCloseSecFlow) { state, autoCloseSec ->
            MainDoorbellState(
                state = state,
                autoCloseSec = autoCloseSec,
            )
        }

    private val sidebarFlow =
        combine(
            sidebarPositionFlow,
            sidebarPinnedFlow,
            sidebarShowLabelsFlow,
            sidebarShortcutsFlow,
            sidebarEdgeHandleFlow,
        ) { position, pinned, showLabels, shortcuts, edgeHandle ->
            SidebarUiState(
                position = position,
                pinned = pinned,
                showLabels = showLabels,
                shortcuts = shortcuts.toImmutableList(),
                edgeHandle = edgeHandle,
            )
        }

    val uiState: StateFlow<MainUiState> =
        combine(
            connectionFlow,
            environmentFlow,
            doorbellFlow,
            sidebarFlow,
        ) { connection, environment, doorbell, sidebar ->
            val effectiveMode =
                ScreensaverMode.fromKey(
                    if (environment.nightActive) {
                        environment.nightKey
                    } else {
                        environment.dayKey
                    },
                )
            val appState: AppState =
                when {
                    connection.haUrl.isNullOrBlank() -> AppState.NeedsSetup
                    connection.tokens == null -> AppState.Configured(connection.haUrl)
                    connection.haState is HaConnectionState.NeedsReauth ->
                        AppState.NeedsReauth(haUrl = connection.haUrl, reason = connection.haState.reason)
                    else -> AppState.Configured(connection.haUrl)
                }
            MainUiState(
                appState = appState,
                dashboardPath = connection.dashboardPath,
                tokens = connection.tokens,
                voiceState = environment.voiceState,
                isIdle = environment.isIdle && effectiveMode != ScreensaverMode.Off,
                nightModeActive = environment.nightActive,
                doorbellState = doorbell.state,
                doorbellAutoCloseSec = doorbell.autoCloseSec,
                haBaseUrl = (appState as? AppState.Configured)?.haUrl ?: "",
                sidebar = sidebar,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState.empty())

    // Drives the LaunchedEffect that starts/stops the foreground VoiceService.
    val voiceServiceShouldRun: StateFlow<Boolean> =
        combine(connectionFlow, voiceEnabledFlow) { connection, voiceEnabled ->
            VoiceServiceRunPolicy.shouldRun(
                voiceEnabled = voiceEnabled,
                haUrl = connection.haUrl,
                tokens = connection.tokens,
                haState = connection.haState,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private data class MainConnectionState(
        val haUrl: String?,
        val dashboardPath: String,
        val tokens: HaTokens?,
        val haState: HaConnectionState,
    )

    private data class MainEnvironmentState(
        val voiceState: VoiceState,
        val isIdle: Boolean,
        val dayKey: String,
        val nightKey: String,
        val nightActive: Boolean,
    )

    private data class MainDoorbellState(
        val state: DoorbellState,
        val autoCloseSec: Int,
    )

    class Factory(
        private val graph: AppGraph,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(
                haUrlFlow = graph.settings.haUrl,
                dashboardPathFlow = graph.kioskSettings.dashboardPath,
                tokensFlow = graph.tokenStore.tokensFlow,
                haStateFlow = graph.haClient.state,
                voiceStateFlow = graph.voiceCoordinator.state,
                isIdleFlow = graph.idleController.isIdle,
                dayScreensaverModeKeyFlow = graph.screensaverSettings.dayMode,
                nightScreensaverModeKeyFlow = graph.screensaverSettings.nightMode,
                nightModeActiveFlow = graph.sleepController.nightModeActive,
                doorbellStateFlow = graph.doorbellOverlayController.state,
                doorbellAutoCloseSecFlow = graph.doorbellSettings.autoCloseSec,
                voiceEnabledFlow = graph.voiceSettings.enabled,
                sidebarPositionFlow = graph.sidebarSettings.position,
                sidebarPinnedFlow = graph.sidebarSettings.pinned,
                sidebarShowLabelsFlow = graph.sidebarSettings.showLabels,
                sidebarShortcutsFlow = graph.sidebarSettings.shortcuts,
                sidebarEdgeHandleFlow = graph.sidebarSettings.edgeHandle,
            ) as T
    }
}

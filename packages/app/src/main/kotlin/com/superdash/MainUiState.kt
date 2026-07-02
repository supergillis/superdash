package com.superdash

import androidx.compose.runtime.Immutable
import com.superdash.doorbell.DoorbellState
import com.superdash.ha.HaTokens
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarSettingsDefaults
import com.superdash.kiosk.SidebarShortcut
import com.superdash.kiosk.ui.AppState
import com.superdash.voice.pipeline.VoiceState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class MainUiState(
    val appState: AppState,
    val dashboardPath: String,
    val tokens: HaTokens?,
    val voiceState: VoiceState,
    val isIdle: Boolean,
    val nightModeActive: Boolean,
    val doorbellState: DoorbellState,
    val doorbellAutoCloseSec: Int,
    val haBaseUrl: String,
    val sidebar: SidebarUiState,
) {
    companion object {
        fun empty(): MainUiState =
            MainUiState(
                appState = AppState.NeedsSetup,
                dashboardPath = "",
                tokens = null,
                voiceState = VoiceState.Idle,
                isIdle = false,
                nightModeActive = false,
                doorbellState = DoorbellState.Idle,
                doorbellAutoCloseSec = 60,
                haBaseUrl = "",
                sidebar = SidebarUiState.empty(),
            )
    }
}

@Immutable
data class SidebarUiState(
    val position: SidebarPosition,
    val pinned: Boolean,
    val showLabels: Boolean,
    val shortcuts: ImmutableList<SidebarShortcut>,
) {
    companion object {
        fun empty(): SidebarUiState =
            SidebarUiState(
                position = SidebarSettingsDefaults.position,
                pinned = SidebarSettingsDefaults.pinned,
                showLabels = SidebarSettingsDefaults.showLabels,
                shortcuts = SidebarSettingsDefaults.shortcuts.toImmutableList(),
            )
    }
}

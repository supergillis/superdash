package com.superdash.settings.ui

import androidx.compose.runtime.Composable
import com.superdash.settings.DeviceSettingsActions
import com.superdash.settings.DeviceSettingsState

@Composable
fun DeviceSettingsSection(
    state: DeviceSettingsState,
    actions: DeviceSettingsActions,
) {
    SettingsSwitchRow(
        label = "Keep screen on",
        checked = state.keepScreenOn,
        onCheckedChange = actions.onKeepScreenOnChange,
    )
    SettingsSwitchRow(
        label = "Launch on boot",
        checked = state.startOnBoot,
        onCheckedChange = actions.onStartOnBootChange,
    )
}

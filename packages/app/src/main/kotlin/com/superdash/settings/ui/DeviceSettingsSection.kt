package com.superdash.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.superdash.R
import com.superdash.settings.DeviceSettingsActions
import com.superdash.settings.DeviceSettingsState

@Composable
fun DeviceSettingsSection(
    state: DeviceSettingsState,
    actions: DeviceSettingsActions,
) {
    SettingsSwitchRow(
        label = stringResource(R.string.settings_device_keep_screen_on),
        checked = state.keepScreenOn,
        onCheckedChange = actions.onKeepScreenOnChange,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.settings_device_launch_on_boot),
        checked = state.startOnBoot,
        onCheckedChange = actions.onStartOnBootChange,
    )
}
